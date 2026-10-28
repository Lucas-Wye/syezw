#[derive(Clone)]
pub struct EnvConfig {
    pub database_url: String,
    pub api_key: String,
}

impl EnvConfig {
    pub fn from_env() -> Self {
        let database_url =
            std::env::var("DATABASE_URL").unwrap_or_else(|_| "sqlite://syezw.db".to_string());
        let api_key = std::env::var("API_KEY").unwrap_or_default();
        Self {
            database_url,
            api_key,
        }
    }
}

pub fn build_db_url(env: &EnvConfig) -> String {
    env.database_url.clone()
}
