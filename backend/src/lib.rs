use actix_web::{web, HttpRequest, HttpResponse, Responder};
use chrono::NaiveDate;
use sqlx::{PgPool, Row};

pub mod db;
pub mod models;

use db::EnvConfig;
use log::{info, warn};
use models::{
    DiaryImageRefItem, DiaryImageSyncItem, DiarySyncItem, EncryptedBlob, ImageFetchRequest,
    ImageFetchResponse, ImageHashListResponse, ImageRefsResponse, ImageRefsUpsertRequest,
    ImageUploadRequest, PeriodMeta, PeriodSyncItem, SyncCounts, SyncDownloadEnvelope,
    SyncDownloadRequest, SyncDownloadResponse, SyncMeta, SyncMetaResponse, SyncUploadRequest,
    SyncUploadResponse, TodoSyncItem,
};

#[derive(Clone)]
pub struct AppState {
    pub env: EnvConfig,
    pub pool: PgPool,
}

/// Constant-time string comparison to prevent timing attacks on API key validation.
fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut result = 0u8;
    for (x, y) in a.iter().zip(b.iter()) {
        result |= x ^ y;
    }
    result == 0
}

fn check_api_key(req: &HttpRequest, state: &AppState) -> Option<HttpResponse> {
    let expected = state.env.api_key.trim();
    if expected.is_empty() {
        return None;
    }
    let provided = req
        .headers()
        .get("X-API-Key")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    if !constant_time_eq(provided.as_bytes(), expected.as_bytes()) {
        return Some(HttpResponse::Unauthorized().body("unauthorized"));
    }
    None
}

pub async fn sync_upload(
    state: web::Data<AppState>,
    req: HttpRequest,
    payload: web::Json<SyncUploadRequest>,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }
    let mut tx = match state.pool.begin().await {
        Ok(tx) => tx,
        Err(e) => {
            warn!("sync_upload: failed to begin transaction: {}", e);
            return Ok(
                HttpResponse::InternalServerError().json(SyncUploadResponse {
                    ok: false,
                    message: format!("db transaction start failed: {}", e),
                    counts: SyncCounts {
                        diaries: 0,
                        todos: 0,
                        periods: 0,
                        images: 0,
                    },
                }),
            );
        }
    };

    for item in &payload.diaries {
        if let Err(e) = upsert_diary(&mut tx, item).await {
            warn!("sync_upload: diary upsert failed: {}", e);
            return Ok(
                HttpResponse::InternalServerError().json(SyncUploadResponse {
                    ok: false,
                    message: format!("diary upsert failed: {}", e),
                    counts: SyncCounts {
                        diaries: 0,
                        todos: 0,
                        periods: 0,
                        images: 0,
                    },
                }),
            );
        }
    }

    for item in &payload.todos {
        if let Err(e) = upsert_todo(&mut tx, item).await {
            warn!("sync_upload: todo upsert failed: {}", e);
            return Ok(
                HttpResponse::InternalServerError().json(SyncUploadResponse {
                    ok: false,
                    message: format!("todo upsert failed: {}", e),
                    counts: SyncCounts {
                        diaries: 0,
                        todos: 0,
                        periods: 0,
                        images: 0,
                    },
                }),
            );
        }
    }

    for item in &payload.periods {
        if let Err(e) = upsert_period(&mut tx, item).await {
            warn!("sync_upload: period upsert failed: {}", e);
            return Ok(
                HttpResponse::InternalServerError().json(SyncUploadResponse {
                    ok: false,
                    message: format!("period upsert failed: {}", e),
                    counts: SyncCounts {
                        diaries: 0,
                        todos: 0,
                        periods: 0,
                        images: 0,
                    },
                }),
            );
        }
    }

    for item in &payload.images {
        if let Err(e) = upsert_image(&mut tx, item).await {
            warn!("sync_upload: image upsert failed: {}", e);
            return Ok(
                HttpResponse::InternalServerError().json(SyncUploadResponse {
                    ok: false,
                    message: format!("image upsert failed: {}", e),
                    counts: SyncCounts {
                        diaries: 0,
                        todos: 0,
                        periods: 0,
                        images: 0,
                    },
                }),
            );
        }
    }

    if let Err(e) = tx.commit().await {
        warn!("sync_upload: commit failed: {}", e);
        return Ok(
            HttpResponse::InternalServerError().json(SyncUploadResponse {
                ok: false,
                message: format!("commit failed: {}", e),
                counts: SyncCounts {
                    diaries: 0,
                    todos: 0,
                    periods: 0,
                    images: 0,
                },
            }),
        );
    }

    let counts = SyncCounts {
        diaries: payload.diaries.len(),
        todos: payload.todos.len(),
        periods: payload.periods.len(),
        images: payload.images.len(),
    };
    info!(
        "sync_upload success: diaries={}, todos={}, periods={}, images={}",
        counts.diaries, counts.todos, counts.periods, counts.images
    );
    Ok(HttpResponse::Ok().json(SyncUploadResponse {
        ok: true,
        message: "ok".to_string(),
        counts,
    }))
}

pub async fn sync_download(
    state: web::Data<AppState>,
    req: HttpRequest,
    payload: web::Json<SyncDownloadRequest>,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }
    let diary_meta: std::collections::HashMap<String, i64> = payload
        .diaries
        .iter()
        .map(|m| (m.uuid.clone(), m.updated_at))
        .collect();
    let todo_meta: std::collections::HashMap<String, i64> = payload
        .todos
        .iter()
        .map(|m| (m.uuid.clone(), m.updated_at))
        .collect();
    let period_meta: std::collections::HashMap<String, i64> = payload
        .periods
        .iter()
        .map(|m| (m.start_date.clone(), m.updated_at))
        .collect();

    let diary_rows = match sqlx::query(
        r#"
        SELECT uuid, author, timestamp, updated_at, payload_iv, payload_data
        FROM diary_sync
        "#,
    )
    .fetch_all(&state.pool)
    .await
    {
        Ok(rows) => rows,
        Err(e) => {
            warn!("sync_download: diary query failed: {}", e);
            return Ok(
                HttpResponse::InternalServerError().json(SyncDownloadEnvelope {
                    ok: false,
                    message: format!("diary query failed: {}", e),
                    counts: SyncCounts {
                        diaries: 0,
                        todos: 0,
                        periods: 0,
                        images: 0,
                    },
                    data: SyncDownloadResponse {
                        diaries: vec![],
                        todos: vec![],
                        periods: vec![],
                        images: vec![],
                    },
                }),
            );
        }
    };
    let diaries = diary_rows
        .into_iter()
        .map(|row| DiarySyncItem {
            uuid: row.get("uuid"),
            author: row.get("author"),
            timestamp: row.get("timestamp"),
            updated_at: row.get("updated_at"),
            payload: EncryptedBlob {
                iv: row.get("payload_iv"),
                data: row.get("payload_data"),
            },
        })
        .filter(|item| match diary_meta.get(&item.uuid) {
            None => true,
            Some(local_updated) => item.updated_at > *local_updated,
        })
        .collect();

    let todo_rows = match sqlx::query(
        r#"
        SELECT uuid, author, is_completed, created_at, completed_at, updated_at, payload_iv, payload_data
        FROM todo_sync
        "#,
    )
    .fetch_all(&state.pool)
    .await
    {
        Ok(rows) => rows,
        Err(e) => {
            warn!("sync_download: todo query failed: {}", e);
            return Ok(HttpResponse::InternalServerError().json(SyncDownloadEnvelope {
                ok: false,
                message: format!("todo query failed: {}", e),
                counts: SyncCounts { diaries: 0, todos: 0, periods: 0, images: 0 },
                data: SyncDownloadResponse { diaries: vec![], todos: vec![], periods: vec![], images: vec![] },
            }));
        }
    };
    let todos = todo_rows
        .into_iter()
        .map(|row| TodoSyncItem {
            uuid: row.get("uuid"),
            author: row.get("author"),
            is_completed: row.get("is_completed"),
            created_at: row.get("created_at"),
            completed_at: row.get("completed_at"),
            updated_at: row.get("updated_at"),
            payload: EncryptedBlob {
                iv: row.get("payload_iv"),
                data: row.get("payload_data"),
            },
        })
        .filter(|item| match todo_meta.get(&item.uuid) {
            None => true,
            Some(local_updated) => item.updated_at > *local_updated,
        })
        .collect();

    let period_rows = match sqlx::query(
        r#"
        SELECT start_date::text as start_date, end_date::text as end_date, updated_at, payload_iv, payload_data
        FROM period_sync
        "#,
    )
    .fetch_all(&state.pool)
    .await
    {
        Ok(rows) => rows,
        Err(e) => {
            warn!("sync_download: period query failed: {}", e);
            return Ok(HttpResponse::InternalServerError().json(SyncDownloadEnvelope {
                ok: false,
                message: format!("period query failed: {}", e),
                counts: SyncCounts { diaries: 0, todos: 0, periods: 0, images: 0 },
                data: SyncDownloadResponse { diaries: vec![], todos: vec![], periods: vec![], images: vec![] },
            }));
        }
    };
    let periods = period_rows
        .into_iter()
        .map(|row| PeriodSyncItem {
            start_date: row.get("start_date"),
            end_date: row.get("end_date"),
            updated_at: row.get("updated_at"),
            payload: EncryptedBlob {
                iv: row.get("payload_iv"),
                data: row.get("payload_data"),
            },
        })
        .filter(|item| match period_meta.get(&item.start_date) {
            None => true,
            Some(local_updated) => item.updated_at > *local_updated,
        })
        .collect();

    // Note: Images are NOT included in sync_download to avoid transferring
    // potentially huge blobs. Clients should use /images/refs + /images/fetch
    // for on-demand image downloads.
    let response = SyncDownloadResponse {
        diaries,
        todos,
        periods,
        images: vec![],
    };
    let counts = SyncCounts {
        diaries: response.diaries.len(),
        todos: response.todos.len(),
        periods: response.periods.len(),
        images: response.images.len(),
    };
    info!(
        "sync_download success: diaries={}, todos={}, periods={}, images={}",
        counts.diaries, counts.todos, counts.periods, counts.images
    );
    Ok(HttpResponse::Ok().json(SyncDownloadEnvelope {
        ok: true,
        message: "ok".to_string(),
        counts,
        data: response,
    }))
}

pub async fn image_fetch(
    state: web::Data<AppState>,
    req: HttpRequest,
    payload: web::Json<ImageFetchRequest>,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }
    let row = sqlx::query(
        r#"
        SELECT r.file_name, r.diary_uuid, r.updated_at, r.hash, i.blob_iv, i.blob_data
        FROM diary_image_refs r
        JOIN diary_images i ON i.hash = r.hash
        WHERE r.diary_uuid = $1 AND r.file_name = $2
        "#,
    )
    .bind(&payload.diary_uuid)
    .bind(&payload.file_name)
    .fetch_one(&state.pool)
    .await
    .map_err(actix_web::error::ErrorNotFound)?;

    let response = ImageFetchResponse {
        file_name: row.get("file_name"),
        diary_uuid: row.get("diary_uuid"),
        hash: row.get("hash"),
        updated_at: row.get("updated_at"),
        blob: EncryptedBlob {
            iv: row.get("blob_iv"),
            data: row.get("blob_data"),
        },
    };
    Ok(HttpResponse::Ok().json(response))
}

pub async fn image_hashes(
    state: web::Data<AppState>,
    req: HttpRequest,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }
    let rows = match sqlx::query("SELECT hash FROM diary_images")
        .fetch_all(&state.pool)
        .await
    {
        Ok(rows) => rows,
        Err(e) => {
            warn!("image_hashes: query failed: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
    };
    let hashes: Vec<String> = rows.into_iter().map(|row| row.get("hash")).collect();
    info!("image_hashes success: {} hashes", hashes.len());
    Ok(HttpResponse::Ok().json(ImageHashListResponse { hashes }))
}

pub async fn image_refs(
    state: web::Data<AppState>,
    req: HttpRequest,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }
    let rows = match sqlx::query(
        r#"
        SELECT diary_uuid, file_name, hash, updated_at
        FROM diary_image_refs
        "#,
    )
    .fetch_all(&state.pool)
    .await
    {
        Ok(rows) => rows,
        Err(e) => {
            warn!("image_refs: query failed: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
    };

    let refs: Vec<DiaryImageRefItem> = rows
        .into_iter()
        .map(|row| DiaryImageRefItem {
            diary_uuid: row.get("diary_uuid"),
            file_name: row.get("file_name"),
            hash: row.get("hash"),
            updated_at: row.get("updated_at"),
        })
        .collect();

    info!("image_refs success: {} refs", refs.len());
    Ok(HttpResponse::Ok().json(ImageRefsResponse { refs }))
}

pub async fn image_upload(
    state: web::Data<AppState>,
    req: HttpRequest,
    payload: web::Json<ImageUploadRequest>,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }
    let mut tx = match state.pool.begin().await {
        Ok(tx) => tx,
        Err(e) => {
            warn!("image_upload: failed to begin transaction: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
    };
    let mut success = 0usize;
    for item in &payload.images {
        if let Err(e) = sqlx::query(
            r#"
            INSERT INTO diary_images (hash, blob_iv, blob_data, updated_at)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (hash) DO UPDATE SET
                blob_iv = EXCLUDED.blob_iv,
                blob_data = EXCLUDED.blob_data,
                updated_at = EXCLUDED.updated_at
            "#,
        )
        .bind(&item.hash)
        .bind(&item.blob.iv)
        .bind(&item.blob.data)
        .bind(item.updated_at)
        .execute(&mut *tx)
        .await
        {
            warn!("image_upload: upsert failed: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
        success += 1;
    }
    if let Err(e) = tx.commit().await {
        warn!("image_upload: commit failed: {}", e);
        return Ok(HttpResponse::InternalServerError().finish());
    }

    info!("image_upload success: {} images", success);
    Ok(HttpResponse::Ok().finish())
}

pub async fn image_refs_upsert(
    state: web::Data<AppState>,
    req: HttpRequest,
    payload: web::Json<ImageRefsUpsertRequest>,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }
    let mut tx = match state.pool.begin().await {
        Ok(tx) => tx,
        Err(e) => {
            warn!("image_refs_upsert: failed to begin transaction: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
    };
    let mut success = 0usize;
    for item in &payload.refs {
        if let Err(e) = sqlx::query(
            r#"
            INSERT INTO diary_image_refs (diary_uuid, file_name, hash, updated_at)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (diary_uuid, file_name) DO UPDATE SET
                hash = EXCLUDED.hash,
                updated_at = EXCLUDED.updated_at
            "#,
        )
        .bind(&item.diary_uuid)
        .bind(&item.file_name)
        .bind(&item.hash)
        .bind(item.updated_at)
        .execute(&mut *tx)
        .await
        {
            warn!("image_refs_upsert: upsert failed: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
        success += 1;
    }
    if let Err(e) = tx.commit().await {
        warn!("image_refs_upsert: commit failed: {}", e);
        return Ok(HttpResponse::InternalServerError().finish());
    }

    info!("image_refs_upsert success: {} refs", success);
    Ok(HttpResponse::Ok().finish())
}

pub async fn sync_meta(
    state: web::Data<AppState>,
    req: HttpRequest,
) -> actix_web::Result<impl Responder> {
    if let Some(resp) = check_api_key(&req, &state) {
        return Ok(resp);
    }

    let diary_rows = match sqlx::query("SELECT uuid, updated_at FROM diary_sync")
        .fetch_all(&state.pool)
        .await
    {
        Ok(rows) => rows,
        Err(e) => {
            warn!("sync_meta: diary query failed: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
    };
    let diaries = diary_rows
        .into_iter()
        .map(|row| SyncMeta {
            uuid: row.get("uuid"),
            updated_at: row.get("updated_at"),
        })
        .collect();

    let todo_rows = match sqlx::query("SELECT uuid, updated_at FROM todo_sync")
        .fetch_all(&state.pool)
        .await
    {
        Ok(rows) => rows,
        Err(e) => {
            warn!("sync_meta: todo query failed: {}", e);
            return Ok(HttpResponse::InternalServerError().finish());
        }
    };
    let todos = todo_rows
        .into_iter()
        .map(|row| SyncMeta {
            uuid: row.get("uuid"),
            updated_at: row.get("updated_at"),
        })
        .collect();

    let period_rows =
        match sqlx::query("SELECT start_date::text as start_date, updated_at FROM period_sync")
            .fetch_all(&state.pool)
            .await
        {
            Ok(rows) => rows,
            Err(e) => {
                warn!("sync_meta: period query failed: {}", e);
                return Ok(HttpResponse::InternalServerError().finish());
            }
        };
    let periods = period_rows
        .into_iter()
        .map(|row| PeriodMeta {
            start_date: row.get("start_date"),
            updated_at: row.get("updated_at"),
        })
        .collect();

    Ok(HttpResponse::Ok().json(SyncMetaResponse {
        diaries,
        todos,
        periods,
    }))
}

async fn upsert_diary(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    item: &DiarySyncItem,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        INSERT INTO diary_sync (uuid, author, timestamp, updated_at, payload_iv, payload_data)
        VALUES ($1, $2, $3, $4, $5, $6)
        ON CONFLICT (uuid) DO UPDATE SET
            author = EXCLUDED.author,
            timestamp = EXCLUDED.timestamp,
            updated_at = EXCLUDED.updated_at,
            payload_iv = EXCLUDED.payload_iv,
            payload_data = EXCLUDED.payload_data
        "#,
    )
    .bind(&item.uuid)
    .bind(&item.author)
    .bind(item.timestamp)
    .bind(item.updated_at)
    .bind(&item.payload.iv)
    .bind(&item.payload.data)
    .execute(&mut **tx)
    .await?;
    Ok(())
}

async fn upsert_todo(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    item: &TodoSyncItem,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        INSERT INTO todo_sync (
            uuid, author, is_completed, created_at, completed_at, updated_at, payload_iv, payload_data
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        ON CONFLICT (uuid) DO UPDATE SET
            author = EXCLUDED.author,
            is_completed = EXCLUDED.is_completed,
            created_at = EXCLUDED.created_at,
            completed_at = EXCLUDED.completed_at,
            updated_at = EXCLUDED.updated_at,
            payload_iv = EXCLUDED.payload_iv,
            payload_data = EXCLUDED.payload_data
        "#,
    )
    .bind(&item.uuid)
    .bind(&item.author)
    .bind(item.is_completed)
    .bind(item.created_at)
    .bind(item.completed_at)
    .bind(item.updated_at)
    .bind(&item.payload.iv)
    .bind(&item.payload.data)
    .execute(&mut **tx)
    .await?;
    Ok(())
}

async fn upsert_period(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    item: &PeriodSyncItem,
) -> Result<(), actix_web::Error> {
    let start_date = NaiveDate::parse_from_str(&item.start_date, "%Y-%m-%d")
        .map_err(actix_web::error::ErrorBadRequest)?;
    let end_date = NaiveDate::parse_from_str(&item.end_date, "%Y-%m-%d")
        .map_err(actix_web::error::ErrorBadRequest)?;
    sqlx::query(
        r#"
        INSERT INTO period_sync (start_date, end_date, updated_at, payload_iv, payload_data)
        VALUES ($1, $2, $3, $4, $5)
        ON CONFLICT (start_date) DO UPDATE SET
            end_date = EXCLUDED.end_date,
            updated_at = EXCLUDED.updated_at,
            payload_iv = EXCLUDED.payload_iv,
            payload_data = EXCLUDED.payload_data
        "#,
    )
    .bind(start_date)
    .bind(end_date)
    .bind(item.updated_at)
    .bind(&item.payload.iv)
    .bind(&item.payload.data)
    .execute(&mut **tx)
    .await
    .map_err(actix_web::error::ErrorInternalServerError)?;
    Ok(())
}

async fn upsert_image(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    item: &DiaryImageSyncItem,
) -> Result<(), sqlx::Error> {
    // Store image blob once per hash.
    sqlx::query(
        r#"
        INSERT INTO diary_images (hash, blob_iv, blob_data, updated_at)
        VALUES ($1, $2, $3, $4)
        ON CONFLICT (hash) DO UPDATE SET
            blob_iv = EXCLUDED.blob_iv,
            blob_data = EXCLUDED.blob_data,
            updated_at = EXCLUDED.updated_at
        "#,
    )
    .bind(&item.hash)
    .bind(&item.blob.iv)
    .bind(&item.blob.data)
    .bind(item.updated_at)
    .execute(&mut **tx)
    .await?;

    // Track diary reference.
    sqlx::query(
        r#"
        INSERT INTO diary_image_refs (diary_uuid, file_name, hash, updated_at)
        VALUES ($1, $2, $3, $4)
        ON CONFLICT (diary_uuid, file_name) DO UPDATE SET
            hash = EXCLUDED.hash,
            updated_at = EXCLUDED.updated_at
        "#,
    )
    .bind(&item.diary_uuid)
    .bind(&item.file_name)
    .bind(&item.hash)
    .bind(item.updated_at)
    .execute(&mut **tx)
    .await?;
    Ok(())
}
