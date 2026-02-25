package org.syezw.sync

data class EncryptedBlob(
    val iv: String,
    val data: String
)

data class DiaryPayload(
    val content: String,
    val tags: List<String>,
    val location: String?,
    val imageUris: List<String>
)

data class DiarySyncItem(
    val uuid: String,
    val author: String,
    val timestamp: Long,
    val updatedAt: Long,
    val payload: EncryptedBlob
)

data class TodoPayload(
    val name: String
)

data class TodoSyncItem(
    val uuid: String,
    val author: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val completedAt: Long?,
    val updatedAt: Long,
    val payload: EncryptedBlob
)

data class PeriodPayload(
    val notes: String?
)

data class PeriodSyncItem(
    val startDate: String,
    val endDate: String,
    val updatedAt: Long,
    val payload: EncryptedBlob
)

data class DiaryImageSyncItem(
    val fileName: String,
    val diaryUuid: String,
    val hash: String,
    val updatedAt: Long,
    val blob: EncryptedBlob
)

data class DiaryImageRefItem(
    val diaryUuid: String,
    val fileName: String,
    val hash: String,
    val updatedAt: Long
)

data class SyncUploadRequest(
    val diaries: List<DiarySyncItem>,
    val todos: List<TodoSyncItem>,
    val periods: List<PeriodSyncItem>,
    val images: List<DiaryImageSyncItem>
)

data class SyncCounts(
    val diaries: Int,
    val todos: Int,
    val periods: Int,
    val images: Int
)

data class SyncUploadResponse(
    val ok: Boolean,
    val message: String,
    val counts: SyncCounts
)

data class SyncDownloadRequest(
    val diaries: List<SyncMeta> = emptyList(),
    val todos: List<SyncMeta> = emptyList(),
    val periods: List<PeriodMeta> = emptyList()
)

data class SyncMeta(
    val uuid: String,
    val updatedAt: Long
)

data class PeriodMeta(
    val startDate: String,
    val updatedAt: Long
)

data class ImageFetchRequest(
    val diaryUuid: String,
    val fileName: String
)

data class ImageFetchResponse(
    val fileName: String,
    val diaryUuid: String,
    val hash: String,
    val updatedAt: Long,
    val blob: EncryptedBlob
)

data class ImageHashListResponse(
    val hashes: List<String>
)

data class ImageRefsResponse(
    val refs: List<DiaryImageRefItem>
)

data class ImageUploadRequest(
    val images: List<DiaryImageSyncItem>
)

data class ImageRefsUpsertRequest(
    val refs: List<DiaryImageRefItem>
)

data class SyncDownloadEnvelope(
    val ok: Boolean,
    val message: String,
    val counts: SyncCounts,
    val data: SyncDownloadResponse
)

data class SyncDownloadResponse(
    val diaries: List<DiarySyncItem>,
    val todos: List<TodoSyncItem>,
    val periods: List<PeriodSyncItem>,
    val images: List<DiaryImageSyncItem>
)


data class SyncMetaResponse(
    val diaries: List<SyncMeta>,
    val todos: List<SyncMeta>,
    val periods: List<PeriodMeta>
)
