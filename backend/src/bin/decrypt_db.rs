use anyhow::{bail, Context, Result};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use md5::{Digest as Md5Digest, Md5};
use ring::aead::{self, Aad, LessSafeKey, Nonce, UnboundKey};
use serde::Deserialize;
use sha2::Sha256;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};
use sqlx::{Row, SqlitePool};
use std::collections::HashMap;
use std::env;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug)]
struct Args {
    db: PathBuf,
    passphrase: String,
    output: PathBuf,
    images_dir: PathBuf,
    // Accepted as requested for command-line compatibility. Local SQLite files do not
    // perform API authentication; the AES passphrase is the actual decryption secret.
    _api_key: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DiaryPayload {
    #[serde(default)]
    content: String,
    #[serde(default)]
    tags: Vec<String>,
    location: Option<String>,
    #[serde(default)]
    image_uris: Vec<String>,
}

#[derive(Deserialize)]
struct TodoPayload {
    #[serde(default)]
    name: String,
}

#[derive(Deserialize)]
struct PeriodPayload {
    notes: Option<String>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProductPayload {
    #[serde(default)]
    merchant: String,
    #[serde(default)]
    price: f64,
    #[serde(default = "one")]
    discount: f64,
    #[serde(default)]
    quantity: f64,
    #[serde(default)]
    quantity_unit: String,
    #[serde(default)]
    notes: String,
}

fn one() -> f64 {
    1.0
}

fn parse_args() -> Result<Args> {
    let mut db = None;
    let mut passphrase = None;
    let mut output = None;
    let mut images_dir = None;
    let mut api_key = None;
    let mut iter = env::args().skip(1);
    while let Some(arg) = iter.next() {
        let slot = match arg.as_str() {
            "--db" | "--db-path" => &mut db,
            "--passphrase" => &mut passphrase,
            "--output" => &mut output,
            "--images-dir" => &mut images_dir,
            "--api-key" => &mut api_key,
            "--help" | "-h" => {
                println!("Usage: cargo run --bin decrypt_db -- --db <syezw.db> --passphrase <AES passphrase> [--api-key <key>] [--output <decrypted.sqlite>] [--images-dir <directory>]");
                std::process::exit(0);
            }
            _ => bail!("unknown argument: {arg}; use --help for usage"),
        };
        let value = iter
            .next()
            .with_context(|| format!("missing value for {arg}"))?;
        *slot = Some(value);
    }

    let db = PathBuf::from(db.context("required argument: --db <path>")?);
    let passphrase = passphrase.context("required argument: --passphrase <AES passphrase>")?;
    let default_output = {
        let stem = db.file_stem().and_then(|s| s.to_str()).unwrap_or("syezw");
        db.with_file_name(format!("{stem}.decrypted.sqlite"))
    };
    let output =
        PathBuf::from(output.unwrap_or_else(|| default_output.to_string_lossy().into_owned()));
    let images_dir = PathBuf::from(images_dir.unwrap_or_else(|| {
        let stem = output
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("decrypted");
        output
            .with_file_name(format!("{stem}_images"))
            .to_string_lossy()
            .into_owned()
    }));
    Ok(Args {
        db,
        passphrase,
        output,
        images_dir,
        _api_key: api_key,
    })
}

fn aes_key(passphrase: &str) -> [u8; 16] {
    let digest = Md5::digest(passphrase.as_bytes());
    let mut key = [0u8; 16];
    key.copy_from_slice(&digest);
    key
}

fn decrypt(iv_b64: &str, data_b64: &str, key_bytes: &[u8; 16]) -> Result<Vec<u8>> {
    let iv = BASE64.decode(iv_b64).context("invalid base64 IV")?;
    let mut ciphertext = BASE64
        .decode(data_b64)
        .context("invalid base64 ciphertext")?;
    let nonce_bytes: [u8; 12] = iv
        .try_into()
        .map_err(|_| anyhow::anyhow!("AES-GCM IV must be 12 bytes"))?;
    let unbound = UnboundKey::new(&aead::AES_128_GCM, key_bytes)
        .map_err(|_| anyhow::anyhow!("invalid AES key"))?;
    let key = LessSafeKey::new(unbound);
    let plaintext = key
        .open_in_place(
            Nonce::assume_unique_for_key(nonce_bytes),
            Aad::empty(),
            &mut ciphertext,
        )
        .map_err(|_| {
            anyhow::anyhow!("AES-GCM authentication failed (wrong passphrase or damaged data)")
        })?;
    Ok(plaintext.to_vec())
}

async fn open_source(path: &Path) -> Result<SqlitePool> {
    if !path.is_file() {
        bail!("database file does not exist: {}", path.display());
    }
    let options = SqliteConnectOptions::new().filename(path).read_only(true);
    SqlitePoolOptions::new()
        .max_connections(1)
        .connect_with(options)
        .await
        .with_context(|| format!("open source database {}", path.display()))
}

async fn open_output(path: &Path) -> Result<SqlitePool> {
    if path.exists() {
        bail!(
            "output database already exists: {} (choose another --output path)",
            path.display()
        );
    }
    if let Some(parent) = path.parent() {
        if !parent.as_os_str().is_empty() {
            fs::create_dir_all(parent)?;
        }
    }
    let options = SqliteConnectOptions::new()
        .filename(path)
        .create_if_missing(true);
    SqlitePoolOptions::new()
        .max_connections(1)
        .connect_with(options)
        .await
        .with_context(|| format!("create output database {}", path.display()))
}

async fn initialize_output(pool: &SqlitePool) -> Result<()> {
    let schema = [
        "CREATE TABLE diary_entries (uuid TEXT PRIMARY KEY, author TEXT NOT NULL, timestamp INTEGER NOT NULL, updated_at INTEGER NOT NULL, content TEXT NOT NULL, tags_json TEXT NOT NULL, location TEXT, image_paths_json TEXT NOT NULL)",
        "CREATE TABLE todo_tasks (uuid TEXT PRIMARY KEY, author TEXT NOT NULL, is_completed INTEGER NOT NULL, created_at INTEGER NOT NULL, completed_at INTEGER, updated_at INTEGER NOT NULL, name TEXT NOT NULL)",
        "CREATE TABLE period_records (start_date TEXT PRIMARY KEY, end_date TEXT NOT NULL, updated_at INTEGER NOT NULL, notes TEXT)",
        "CREATE TABLE product_offers (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, timestamp INTEGER NOT NULL, updated_at INTEGER NOT NULL, merchant TEXT NOT NULL, price REAL NOT NULL, discount REAL NOT NULL, quantity REAL NOT NULL, quantity_unit TEXT NOT NULL, notes TEXT NOT NULL)",
        "CREATE TABLE diary_images (hash TEXT PRIMARY KEY, path TEXT NOT NULL)",
        "CREATE TABLE diary_image_refs (diary_uuid TEXT NOT NULL, file_name TEXT NOT NULL, hash TEXT NOT NULL, path TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (diary_uuid, file_name))",
    ];
    for statement in schema {
        sqlx::query(statement).execute(pool).await?;
    }
    Ok(())
}

fn image_extension(file_name: &str) -> &str {
    Path::new(file_name)
        .extension()
        .and_then(|ext| ext.to_str())
        .filter(|ext| {
            !ext.is_empty() && ext.len() <= 10 && ext.bytes().all(|b| b.is_ascii_alphanumeric())
        })
        .unwrap_or("bin")
}

async fn decrypt_images(
    source: &SqlitePool,
    output: &SqlitePool,
    directory: &Path,
    key: &[u8; 16],
) -> Result<HashMap<(String, String), String>> {
    let refs = sqlx::query("SELECT diary_uuid, file_name, hash, updated_at FROM diary_image_refs ORDER BY diary_uuid, file_name")
        .fetch_all(source).await.context("read image references")?;
    let mut path_by_ref = HashMap::new();
    let mut path_by_hash: HashMap<String, String> = HashMap::new();
    for reference in refs {
        let diary_uuid: String = reference.get("diary_uuid");
        let file_name: String = reference.get("file_name");
        let hash: String = reference.get("hash");
        let updated_at: i64 = reference.get("updated_at");
        if hash.len() != 64 || !hash.bytes().all(|byte| byte.is_ascii_hexdigit()) {
            bail!("invalid SHA-256 image hash for {file_name}: {hash}");
        }
        let image_path = if let Some(path) = path_by_hash.get(&hash) {
            path.clone()
        } else {
            let row = sqlx::query("SELECT blob_iv, blob_data FROM diary_images WHERE hash = ?")
                .bind(&hash)
                .fetch_optional(source)
                .await?
                .with_context(|| format!("no image blob found for hash {hash}"))?;
            let plain = decrypt(
                &row.get::<String, _>("blob_iv"),
                &row.get::<String, _>("blob_data"),
                key,
            )
            .with_context(|| format!("decrypt image {file_name} (hash {hash})"))?;
            use sha2::Digest;
            let actual_hash = format!("{:x}", Sha256::digest(&plain));
            if actual_hash != hash.to_ascii_lowercase() {
                bail!("image hash mismatch for {file_name}: expected {hash}, got {actual_hash}");
            }
            let safe_hash = hash.to_ascii_lowercase();
            let path = directory.join(format!("{}.{}", safe_hash, image_extension(&file_name)));
            fs::write(&path, plain).with_context(|| format!("write image {}", path.display()))?;
            let path = path.canonicalize()?.to_string_lossy().into_owned();
            sqlx::query("INSERT INTO diary_images (hash, path) VALUES (?, ?)")
                .bind(&hash)
                .bind(&path)
                .execute(output)
                .await?;
            path_by_hash.insert(hash.clone(), path.clone());
            path
        };
        sqlx::query("INSERT INTO diary_image_refs (diary_uuid, file_name, hash, path, updated_at) VALUES (?, ?, ?, ?, ?)")
            .bind(&diary_uuid).bind(&file_name).bind(&hash).bind(&image_path).bind(updated_at).execute(output).await?;
        path_by_ref.insert((diary_uuid, file_name), image_path);
    }
    Ok(path_by_ref)
}

async fn decrypt_records(
    source: &SqlitePool,
    output: &SqlitePool,
    key: &[u8; 16],
    images: &HashMap<(String, String), String>,
) -> Result<()> {
    for row in sqlx::query(
        "SELECT uuid, author, timestamp, updated_at, payload_iv, payload_data FROM diary_sync",
    )
    .fetch_all(source)
    .await
    .context("read diaries")?
    {
        let uuid: String = row.get("uuid");
        let payload: DiaryPayload = serde_json::from_slice(
            &decrypt(
                &row.get::<String, _>("payload_iv"),
                &row.get::<String, _>("payload_data"),
                key,
            )
            .with_context(|| format!("decrypt diary {uuid}"))?,
        )
        .with_context(|| format!("parse diary {uuid}"))?;
        let image_paths: Vec<String> = payload
            .image_uris
            .iter()
            .filter_map(|name| images.get(&(uuid.clone(), name.clone())).cloned())
            .collect();
        sqlx::query("INSERT INTO diary_entries (uuid, author, timestamp, updated_at, content, tags_json, location, image_paths_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
            .bind(&uuid).bind(row.get::<String, _>("author")).bind(row.get::<i64, _>("timestamp")).bind(row.get::<i64, _>("updated_at"))
            .bind(payload.content).bind(serde_json::to_string(&payload.tags)?).bind(payload.location).bind(serde_json::to_string(&image_paths)?)
            .execute(output).await?;
    }
    for row in sqlx::query("SELECT uuid, author, is_completed, created_at, completed_at, updated_at, payload_iv, payload_data FROM todo_sync").fetch_all(source).await.context("read todos")? {
        let uuid: String = row.get("uuid");
        let payload: TodoPayload = serde_json::from_slice(&decrypt(&row.get::<String, _>("payload_iv"), &row.get::<String, _>("payload_data"), key).with_context(|| format!("decrypt todo {uuid}"))?).with_context(|| format!("parse todo {uuid}"))?;
        sqlx::query("INSERT INTO todo_tasks (uuid, author, is_completed, created_at, completed_at, updated_at, name) VALUES (?, ?, ?, ?, ?, ?, ?)")
            .bind(&uuid).bind(row.get::<String, _>("author")).bind(row.get::<bool, _>("is_completed")).bind(row.get::<i64, _>("created_at"))
            .bind(row.get::<Option<i64>, _>("completed_at")).bind(row.get::<i64, _>("updated_at")).bind(payload.name).execute(output).await?;
    }
    for row in sqlx::query(
        "SELECT start_date, end_date, updated_at, payload_iv, payload_data FROM period_sync",
    )
    .fetch_all(source)
    .await
    .context("read periods")?
    {
        let start_date: String = row.get("start_date");
        let payload: PeriodPayload = serde_json::from_slice(
            &decrypt(
                &row.get::<String, _>("payload_iv"),
                &row.get::<String, _>("payload_data"),
                key,
            )
            .with_context(|| format!("decrypt period {start_date}"))?,
        )
        .with_context(|| format!("parse period {start_date}"))?;
        sqlx::query("INSERT INTO period_records (start_date, end_date, updated_at, notes) VALUES (?, ?, ?, ?)")
            .bind(start_date).bind(row.get::<String, _>("end_date")).bind(row.get::<i64, _>("updated_at")).bind(payload.notes).execute(output).await?;
    }
    for row in sqlx::query("SELECT id, name, timestamp, updated_at, discount, notes, payload_iv, payload_data FROM product_sync").fetch_all(source).await.context("read products")? {
        let uuid: String = row.get("id");
        let payload: ProductPayload = serde_json::from_slice(&decrypt(&row.get::<String, _>("payload_iv"), &row.get::<String, _>("payload_data"), key).with_context(|| format!("decrypt product {uuid}"))?).with_context(|| format!("parse product {uuid}"))?;
        let notes = if payload.notes.trim().is_empty() {
            row.get::<String, _>("notes")
        } else {
            payload.notes
        };
        sqlx::query("INSERT INTO product_offers (uuid, name, timestamp, updated_at, merchant, price, discount, quantity, quantity_unit, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .bind(&uuid).bind(row.get::<String, _>("name")).bind(row.get::<i64, _>("timestamp")).bind(row.get::<i64, _>("updated_at"))
            .bind(payload.merchant).bind(payload.price).bind(payload.discount).bind(payload.quantity).bind(payload.quantity_unit).bind(notes).execute(output).await?;
    }
    Ok(())
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = parse_args()?;
    if args.passphrase.is_empty() {
        bail!("--passphrase cannot be empty");
    }
    let db_abs = args
        .db
        .canonicalize()
        .context("resolve input database path")?;
    let output_abs = if args.output.exists() {
        args.output.canonicalize()?
    } else {
        args.output.clone()
    };
    if db_abs == output_abs {
        bail!("input and output database paths must differ");
    }
    fs::create_dir_all(&args.images_dir)
        .with_context(|| format!("create image directory {}", args.images_dir.display()))?;
    let source = open_source(&args.db).await?;
    let output = open_output(&args.output).await?;
    initialize_output(&output).await?;
    let key = aes_key(&args.passphrase);
    let images = decrypt_images(&source, &output, &args.images_dir, &key).await?;
    decrypt_records(&source, &output, &key, &images).await?;
    output.close().await;
    source.close().await;
    println!(
        "Decrypted SQLite database: {}",
        args.output.canonicalize()?.display()
    );
    println!(
        "Decrypted images: {}",
        args.images_dir.canonicalize()?.display()
    );
    println!("Image references with paths: {}", images.len());
    Ok(())
}
