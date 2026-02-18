use crate::models::DbConfig;

#[derive(Clone)]
pub struct EnvConfig {
    pub host: String,
    pub port: i32,
    pub database: String,
    pub user: String,
    pub password: String,
    pub api_key: String,
}

impl EnvConfig {
    pub fn from_env() -> Self {
        let host = std::env::var("PG_HOST").unwrap_or_else(|_| "localhost".to_string());
        let port = std::env::var("PG_PORT")
            .ok()
            .and_then(|v| v.parse::<i32>().ok())
            .unwrap_or(5432);
        let database = std::env::var("PG_DB").unwrap_or_else(|_| "syezw".to_string());
        let user = std::env::var("PG_USER").unwrap_or_else(|_| "postgres".to_string());
        let password = std::env::var("PG_PASSWORD").unwrap_or_else(|_| "postgres".to_string());
        let api_key = std::env::var("API_KEY").unwrap_or_default();
        Self {
            host,
            port,
            database,
            user,
            password,
            api_key,
        }
    }
}

pub fn build_db_url(_db: &DbConfig, env: &EnvConfig) -> String {
    let host = &env.host;
    let port = env.port;
    let database = &env.database;
    let user = &env.user;
    let password = &env.password;
    format!(
        "postgresql://{}:{}@{}:{}/{}",
        user, password, host, port, database
    )
}
