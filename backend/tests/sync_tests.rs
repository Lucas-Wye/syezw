use actix_web::{test, web, App};
use sqlx::sqlite::SqlitePoolOptions;

use std::time::{SystemTime, UNIX_EPOCH};
use syezw_sync_backend::db::EnvConfig;
use syezw_sync_backend::models::{
    DiaryImageSyncItem, DiarySyncItem, EncryptedBlob, PeriodSyncItem, ProductSyncItem,
    SyncDownloadEnvelope, SyncDownloadRequest, SyncUploadRequest, TodoSyncItem,
};

async fn test_pool() -> sqlx::SqlitePool {
    let pool = SqlitePoolOptions::new()
        .max_connections(1)
        .connect("sqlite::memory:") // 使用内存作为数据库
        .await
        .expect("connect in-memory SQLite test db");
    for statement in include_str!("../sql/schema.sql")
        .split(';')
        .map(str::trim)
        .filter(|s| !s.is_empty())
    {
        sqlx::query(statement)
            .execute(&pool)
            .await
            .expect("apply schema");
    }
    pool
}

#[actix_web::test]
async fn upload_then_download_round_trip() {
    let pool = test_pool().await;

    let suffix = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let diary_uuid = format!("d1_{}", suffix);
    let todo_uuid = format!("t1_{}", suffix);
    let product_id = format!("p1_{}", suffix);

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

    let upload = SyncUploadRequest {
        diaries: vec![DiarySyncItem {
            uuid: diary_uuid.clone(),
            author: "a".to_string(),
            timestamp: 1,
            updated_at: 2,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        todos: vec![TodoSyncItem {
            uuid: todo_uuid.clone(),
            author: "a".to_string(),
            is_completed: false,
            created_at: 3,
            completed_at: None,
            updated_at: 4,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        periods: vec![PeriodSyncItem {
            start_date: "2025-01-01".to_string(),
            end_date: "2025-01-05".to_string(),
            updated_at: 5,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        images: vec![DiaryImageSyncItem {
            file_name: "img.jpg".to_string(),
            diary_uuid: diary_uuid.clone(),
            hash: "hash123".to_string(),
            updated_at: 6,
            blob: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        products: vec![ProductSyncItem {
            id: product_id.clone(),
            name: "牛奶".to_string(),
            timestamp: 7,
            updated_at: 8,
            discount: 0.8,
            notes: "早餐".to_string(),
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "product".to_string(),
            },
        }],
    };

    let req = test::TestRequest::post()
        .uri("/sync/upload")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap_or_default()))
        .set_json(&upload)
        .to_request();
    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let download_req = SyncDownloadRequest {
        diaries: vec![],
        todos: vec![],
        periods: vec![],
        products: vec![],
    };
    let req = test::TestRequest::post()
        .uri("/sync/download")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap_or_default()))
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
        .any(|p| p.start_date == "2025-01-01" && p.end_date == "2025-01-05"));
    assert!(data
        .products
        .iter()
        .any(|p| p.id == product_id && p.name == "牛奶" && p.discount == 0.8 && p.notes == "早餐"));
    // Images are no longer included in sync_download (fetched via /images/* endpoints)
}

#[actix_web::test]
async fn upload_with_image_hash_dedup_and_fetch() {
    let pool = test_pool().await;

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

    let upload = SyncUploadRequest {
        diaries: vec![DiarySyncItem {
            uuid: diary_uuid.clone(),
            author: "a".to_string(),
            timestamp: 1,
            updated_at: 2,
            payload: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        todos: vec![],
        periods: vec![],
        images: vec![DiaryImageSyncItem {
            file_name: "img.jpg".to_string(),
            diary_uuid: diary_uuid.clone(),
            hash: "hash123".to_string(),
            updated_at: 6,
            blob: EncryptedBlob {
                iv: "iv".to_string(),
                data: "data".to_string(),
            },
        }],
        products: vec![],
    };

    let req = test::TestRequest::post()
        .uri("/sync/upload")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap_or_default()))
        .set_json(&upload)
        .to_request();
    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    // Upload images (simulate new hash)
    let req = test::TestRequest::post()
        .uri("/images/upload")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap_or_default()))
        .set_json(&syezw_sync_backend::models::ImageUploadRequest {
            images: vec![DiaryImageSyncItem {
                file_name: "img.jpg".to_string(),
                diary_uuid: diary_uuid.clone(),
                hash: "hash123".to_string(),
                updated_at: 6,
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
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap_or_default()))
        .set_json(&syezw_sync_backend::models::ImageRefsUpsertRequest {
            refs: vec![syezw_sync_backend::models::DiaryImageRefItem {
                diary_uuid: diary_uuid.clone(),
                file_name: "img.jpg".to_string(),
                hash: "hash123".to_string(),
                updated_at: 6,
            }],
        })
        .to_request();
    let resp = test::call_service(&app, req).await;
    assert!(resp.status().is_success());

    let req = test::TestRequest::post()
        .uri("/images/fetch")
        .insert_header(("X-API-Key", std::env::var("API_KEY").unwrap_or_default()))
        .set_json(&syezw_sync_backend::models::ImageFetchRequest {
            diary_uuid: diary_uuid,
            file_name: "img.jpg".to_string(),
        })
        .to_request();
    let resp =
        test::call_and_read_body_json::<_, _, syezw_sync_backend::models::ImageFetchResponse>(
            &app, req,
        )
        .await;
    assert_eq!(resp.hash, "hash123");
}
