use actix_web::{middleware::Logger, web, App, HttpServer};
use dotenvy::dotenv;
use env_logger::Env;
use log::info;
use sqlx::postgres::PgPoolOptions;
use syezw_sync_backend::db::{build_db_url, EnvConfig};
use syezw_sync_backend::{
    image_fetch, image_hashes, image_refs, image_refs_upsert, image_upload, sync_download,
    sync_upload, AppState,
};

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenv().ok();
    env_logger::init_from_env(Env::default().default_filter_or("info"));
    let env = EnvConfig::from_env();
    let db_url = build_db_url(&env);
    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&db_url)
        .await
        .expect("connect database");
    let bind_addr = std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".to_string());
    info!("Starting server on {}", bind_addr);

    HttpServer::new(move || {
        let json_cfg = web::JsonConfig::default().limit(50 * 1024 * 1024); // 50 MB limit for image uploads
        App::new()
            .wrap(Logger::default())
            .app_data(json_cfg)
            .app_data(web::Data::new(AppState {
                env: env.clone(),
                pool: pool.clone(),
            }))
            .route("/sync/upload", web::post().to(sync_upload))
            .route("/sync/download", web::post().to(sync_download))
            .route("/sync/meta", web::post().to(syezw_sync_backend::sync_meta))
            .route("/images/fetch", web::post().to(image_fetch))
            .route("/images/hashes", web::post().to(image_hashes))
            .route("/images/refs", web::post().to(image_refs))
            .route("/images/upload", web::post().to(image_upload))
            .route("/images/refs/upsert", web::post().to(image_refs_upsert))
    })
    .bind(bind_addr)?
    .run()
    .await
}
