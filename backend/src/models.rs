use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct EncryptedBlob {
    pub iv: String,
    pub data: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DiarySyncItem {
    pub uuid: String,
    pub author: String,
    pub timestamp: i64,
    pub updated_at: i64,
    pub payload: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TodoSyncItem {
    pub uuid: String,
    pub author: String,
    pub is_completed: bool,
    pub created_at: i64,
    pub completed_at: Option<i64>,
    pub updated_at: i64,
    pub payload: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PeriodSyncItem {
    pub start_date: String,
    pub end_date: String,
    pub updated_at: i64,
    pub payload: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DiaryImageSyncItem {
    pub file_name: String,
    pub diary_uuid: String,
    pub hash: String,
    pub updated_at: i64,
    pub blob: EncryptedBlob,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SyncUploadRequest {
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
    #[serde(default)]
    pub diaries: Vec<SyncMeta>,
    #[serde(default)]
    pub todos: Vec<SyncMeta>,
    #[serde(default)]
    pub periods: Vec<PeriodMeta>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct SyncMeta {
    pub uuid: String,
    pub updated_at: i64,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct PeriodMeta {
    pub start_date: String,
    pub updated_at: i64,
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
#[serde(rename_all = "camelCase")]
pub struct DiaryImageRefItem {
    pub diary_uuid: String,
    pub file_name: String,
    pub hash: String,
    pub updated_at: i64,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageUploadRequest {
    pub images: Vec<DiaryImageSyncItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ImageRefsUpsertRequest {
    pub refs: Vec<DiaryImageRefItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ImageFetchRequest {
    pub diary_uuid: String,
    pub file_name: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ImageFetchResponse {
    pub file_name: String,
    pub diary_uuid: String,
    pub hash: String,
    pub updated_at: i64,
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
pub struct SyncMetaResponse {
    pub diaries: Vec<SyncMeta>,
    pub todos: Vec<SyncMeta>,
    pub periods: Vec<PeriodMeta>,
}
