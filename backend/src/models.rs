use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct EncryptedBlob {
    pub iv: String,
    pub data: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct DbConfig {
    pub host: String,
    pub port: i32,
    pub database: String,
    pub user: String,
    pub password: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct DiarySyncItem {
    pub uuid: String,
    pub author: String,
    pub timestamp: i64,
    pub updatedAt: i64,
    pub payload: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TodoSyncItem {
    pub uuid: String,
    pub author: String,
    pub isCompleted: bool,
    pub createdAt: i64,
    pub completedAt: Option<i64>,
    pub updatedAt: i64,
    pub payload: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct PeriodSyncItem {
    pub startDate: String,
    pub endDate: String,
    pub updatedAt: i64,
    pub payload: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct DiaryImageSyncItem {
    pub fileName: String,
    pub diaryUuid: String,
    pub hash: String,
    pub updatedAt: i64,
    pub blob: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncUploadRequest {
    pub db: DbConfig,
    pub diaries: Vec<DiarySyncItem>,
    pub todos: Vec<TodoSyncItem>,
    pub periods: Vec<PeriodSyncItem>,
    pub images: Vec<DiaryImageSyncItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncCounts {
    pub diaries: usize,
    pub todos: usize,
    pub periods: usize,
    pub images: usize,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncUploadResponse {
    pub ok: bool,
    pub message: String,
    pub counts: SyncCounts,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncDownloadEnvelope {
    pub ok: bool,
    pub message: String,
    pub counts: SyncCounts,
    pub data: SyncDownloadResponse,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncDownloadRequest {
    pub db: DbConfig,
    #[serde(default)]
    pub diaries: Vec<SyncMeta>,
    #[serde(default)]
    pub todos: Vec<SyncMeta>,
    #[serde(default)]
    pub periods: Vec<PeriodMeta>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncMeta {
    pub uuid: String,
    pub updatedAt: i64,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct PeriodMeta {
    pub startDate: String,
    pub updatedAt: i64,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageHashListResponse {
    pub hashes: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageRefsResponse {
    pub refs: Vec<DiaryImageRefItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct DiaryImageRefItem {
    pub diaryUuid: String,
    pub fileName: String,
    pub hash: String,
    pub updatedAt: i64,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageUploadRequest {
    pub db: DbConfig,
    pub images: Vec<DiaryImageSyncItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageRefsUpsertRequest {
    pub db: DbConfig,
    pub refs: Vec<DiaryImageRefItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageFetchRequest {
    pub db: DbConfig,
    pub diaryUuid: String,
    pub fileName: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageFetchResponse {
    pub fileName: String,
    pub diaryUuid: String,
    pub hash: String,
    pub updatedAt: i64,
    pub blob: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncDownloadResponse {
    pub diaries: Vec<DiarySyncItem>,
    pub todos: Vec<TodoSyncItem>,
    pub periods: Vec<PeriodSyncItem>,
    pub images: Vec<DiaryImageSyncItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncMetaRequest {
    pub db: DbConfig,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncMetaResponse {
    pub diaries: Vec<SyncMeta>,
    pub todos: Vec<SyncMeta>,
    pub periods: Vec<PeriodMeta>,
}
