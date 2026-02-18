use actix_web::{test, web, App};
use dotenvy::dotenv;
use sqlx::postgres::PgPoolOptions;
use sqlx::Executor;
use std::env;

use std::time::{SystemTime, UNIX_EPOCH};
use syezw_sync_backend::db::EnvConfig;
use syezw_sync_backend::models::{
    DbConfig, DiaryImageSyncItem, DiarySyncItem, EncryptedBlob, PeriodSyncItem,
    SyncDownloadEnvelope, SyncDownloadRequest, SyncUploadRequest, TodoSyncItem,
};

fn log_db_info(label: &str, host: &str, port: i32, db: &str, user: &str) {
    eprintln!(
        "[{}] DB config: host={}, port={}, db={}, user={}",
        label, host, port, db, user
    );
}

#[actix_web::test]
async fn upload_then_download_round_trip() {
    dotenv().ok();
    let test_host = env::var("PG_HOST").unwrap_or_default();
    let test_port = env::var("PG_PORT")
        .ok()
        .and_then(|v| v.parse::<i32>().ok())
        .unwrap_or(0);
    let test_db = env::var("TEST_PG_DB").unwrap_or_default();
    let test_user = env::var("PG_USER").unwrap_or_default();
    let test_password = env::var("PG_PASSWORD").unwrap_or_default();
    if test_host.is_empty() || test_db.is_empty() || test_user.is_empty() {
        eprintln!(
            "TEST_PG_* vars not set, skipping integration test. Missing: {}{}{}",
            if test_host.is_empty() { "PG_HOST " } else { "" },
            if test_db.is_empty() {
                "TEST_PG_DB "
            } else {
                ""
            },
            if test_user.is_empty() { "PG_USER " } else { "" }
        );
        return;
    }

    log_db_info(
        "upload_then_download_round_trip",
        &test_host,
        if test_port == 0 { 5432 } else { test_port },
        &test_db,
        &test_user,
    );

    let test_db_url = format!(
        "postgres://{}:{}@{}:{}/{}",
        test_user,
        test_password,
        test_host,
        if test_port == 0 { 5432 } else { test_port },
        test_db
    );

    let pool = PgPoolOptions::new()
        .max_connections(1)
        .connect(&test_db_url)
        .await
        .expect("connect test db");

    let schema = std::fs::read_to_string("sql/schema.sql").expect("read schema");
    pool.execute(schema.as_str()).await.expect("apply schema");

    let suffix = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let diary_uuid = format!("d1_{}", suffix);
    let todo_uuid = format!("t1_{}", suffix);

    let env_cfg = EnvConfig::from_env();
    let app = test::init_service(
        App::new()
            .app_data(web::Data::new(syezw_sync_backend::AppState {
                env: env_cfg.clone(),
                pool: pool.clone(),
            }))
            .route(
                "/sync/upload",
                web::post().to(syezw_sync_backend::sync_upload),
            )
            .route(
                "/sync/download",
                web::post().to(syezw_sync_backend::sync_download),
            )
            .route("/sync/meta", web::post().to(syezw_sync_backend::sync_meta)),
    )
    .await;

    let db_cfg = DbConfig {
        host: test_host,
        port: if test_port == 0 { 5432 } else { test_port },
        database: test_db,
        user: test_user,
        password: test_password,
    };

    let upload = SyncUploadRequest {
        db: db_cfg.clone(),
        diaries: vec![DiarySyncItem {
            uuid: diary_uuid.clone(),
            author: "a".to_string(),
            timestamp: 1,
            updatedAt: 2,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        todos: vec![TodoSyncItem {
            uuid: todo_uuid.clone(),
            author: "a".to_string(),
            isCompleted: false,
            createdAt: 3,
            completedAt: None,
            updatedAt: 4,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        periods: vec![PeriodSyncItem {
            startDate: "2025-01-01".to_string(),
            endDate: "2025-01-05".to_string(),
            updatedAt: 5,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        images: vec![DiaryImageSyncItem {
            fileName: "img.jpg".to_string(),
            diaryUuid: diary_uuid.clone(),
            hash: "hash123".to_string(),
            updatedAt: 6,
            blob: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
    };

    let req = test::TestRequest::post()
        .uri("/sync/upload")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap()))
        .set_json(&upload)
        .to_request();
    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let download_req = SyncDownloadRequest {
        db: db_cfg,
        diaries: vec![],
        todos: vec![],
        periods: vec![],
    };
    let req = test::TestRequest::post()
        .uri("/sync/download")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap()))
        .set_json(&download_req)
        .to_request();
    let resp: SyncDownloadEnvelope = test::call_and_read_body_json(&app, req).await;
    assert!(resp.ok, "download ok");
    let data = resp.data;

    assert!(data.diaries.iter().any(|d| d.uuid == diary_uuid));
    assert!(data.todos.iter().any(|t| t.uuid == todo_uuid));
    assert!(data
        .periods
        .iter()
        .any(|p| p.startDate == "2025-01-01" && p.endDate == "2025-01-05"));
    assert!(data
        .images
        .iter()
        .any(|img| img.diaryUuid == diary_uuid && img.fileName == "img.jpg"));
}

#[actix_web::test]
async fn upload_with_image_hash_dedup_and_fetch() {
    dotenv().ok();
    let test_host = env::var("PG_HOST").unwrap_or_default();
    let test_port = env::var("PG_PORT")
        .ok()
        .and_then(|v| v.parse::<i32>().ok())
        .unwrap_or(0);
    let test_db = env::var("TEST_PG_DB").unwrap_or_default();
    let test_user = env::var("PG_USER").unwrap_or_default();
    let test_password = env::var("PG_PASSWORD").unwrap_or_default();
    if test_host.is_empty() || test_db.is_empty() || test_user.is_empty() {
        eprintln!(
            "TEST_PG_* vars not set, skipping integration test. Missing: {}{}{}",
            if test_host.is_empty() { "PG_HOST " } else { "" },
            if test_db.is_empty() {
                "TEST_PG_DB "
            } else {
                ""
            },
            if test_user.is_empty() { "PG_USER " } else { "" }
        );
        return;
    }

    log_db_info(
        "upload_with_image_hash_dedup_and_fetch",
        &test_host,
        if test_port == 0 { 5432 } else { test_port },
        &test_db,
        &test_user,
    );

    let test_db_url = format!(
        "postgres://{}:{}@{}:{}/{}",
        test_user,
        test_password,
        test_host,
        if test_port == 0 { 5432 } else { test_port },
        test_db
    );

    let pool = PgPoolOptions::new()
        .max_connections(1)
        .connect(&test_db_url)
        .await
        .expect("connect test db");

    let schema = std::fs::read_to_string("sql/schema.sql").expect("read schema");
    pool.execute(schema.as_str()).await.expect("apply schema");

    let suffix = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let diary_uuid = format!("d_img_1_{}", suffix);

    let env_cfg = EnvConfig::from_env();
    let app = test::init_service(
        App::new()
            .app_data(web::Data::new(syezw_sync_backend::AppState {
                env: env_cfg.clone(),
                pool: pool.clone(),
            }))
            .route(
                "/sync/upload",
                web::post().to(syezw_sync_backend::sync_upload),
            )
            .route(
                "/sync/download",
                web::post().to(syezw_sync_backend::sync_download),
            )
            .route("/sync/meta", web::post().to(syezw_sync_backend::sync_meta))
            .route(
                "/images/hashes",
                web::post().to(syezw_sync_backend::image_hashes),
            )
            .route(
                "/images/upload",
                web::post().to(syezw_sync_backend::image_upload),
            )
            .route(
                "/images/refs/upsert",
                web::post().to(syezw_sync_backend::image_refs_upsert),
            )
            .route(
                "/images/refs",
                web::post().to(syezw_sync_backend::image_refs),
            )
            .route(
                "/images/fetch",
                web::post().to(syezw_sync_backend::image_fetch),
            ),
    )
    .await;

    let db_cfg = DbConfig {
        host: test_host,
        port: if test_port == 0 { 5432 } else { test_port },
        database: test_db,
        user: test_user,
        password: test_password,
    };

    let upload = SyncUploadRequest {
        db: db_cfg.clone(),
        diaries: vec![DiarySyncItem {
            uuid: diary_uuid.clone(),
            author: "a".to_string(),
            timestamp: 1,
            updatedAt: 2,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        todos: vec![],
        periods: vec![],
        images: vec![DiaryImageSyncItem {
            fileName: "img.jpg".to_string(),
            diaryUuid: diary_uuid.clone(),
            hash: "hash123".to_string(),
            updatedAt: 6,
            blob: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
    };

    let req = test::TestRequest::post()
        .uri("/sync/upload")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap()))
        .set_json(&upload)
        .to_request();
    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    // Upload images (simulate new hash)
    let req = test::TestRequest::post()
        .uri("/images/upload")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap()))
        .set_json(&syezw_sync_backend::models::ImageUploadRequest {
            db: db_cfg.clone(),
            images: vec![DiaryImageSyncItem {
                fileName: "img.jpg".to_string(),
                diaryUuid: diary_uuid.clone(),
                hash: "hash123".to_string(),
                updatedAt: 6,
                blob: EncryptedBlob {
                    iv: "iv".to_string(),
                    data: "data".to_string(),
                },
            }],
        })
        .to_request();
    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let req = test::TestRequest::post()
        .uri("/images/refs/upsert")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap()))
        .set_json(&syezw_sync_backend::models::ImageRefsUpsertRequest {
            db: db_cfg.clone(),
            refs: vec![syezw_sync_backend::models::DiaryImageRefItem {
                diaryUuid: diary_uuid.clone(),
                fileName: "img.jpg".to_string(),
                hash: "hash123".to_string(),
                updatedAt: 6,
            }],
        })
        .to_request();
    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let req = test::TestRequest::post()
        .uri("/images/fetch")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap()))
        .set_json(&syezw_sync_backend::models::ImageFetchRequest {
            db: db_cfg.clone(),
            diaryUuid: diary_uuid,
            fileName: "img.jpg".to_string(),
        })
        .to_request();
    let resp =
        test::call_and_read_body_json::<_, _, syezw_sync_backend::models::ImageFetchResponse>(
            &app, req,
        )
        .await;
    assert_eq!(resp.hash, "hash123");
}
