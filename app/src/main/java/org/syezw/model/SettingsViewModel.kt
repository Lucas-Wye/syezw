package org.syezw.model

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.syezw.data.AppDatabase
import org.syezw.data.Diary
import org.syezw.data.TodoTask
import org.syezw.model.PeriodRecord
import org.syezw.worker.BackupWorker
import org.syezw.util.DIARY_IMAGES_FOLDER
import org.syezw.util.diaryImagesRelativePath
import org.syezw.util.resolvePathFromDownloadsRelativePath
import org.syezw.util.resolveDiaryImagePath
import org.syezw.util.resolveDiaryImageFile
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import android.provider.MediaStore
import android.os.Environment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import org.syezw.sync.DbConfig
import org.syezw.sync.DiaryImageSyncItem
import org.syezw.sync.DiaryImageRefItem
import org.syezw.sync.DiaryPayload
import org.syezw.sync.DiarySyncItem
import org.syezw.sync.ImageFetchRequest
import org.syezw.sync.ImageFetchResponse
import org.syezw.sync.ImageHashListResponse
import org.syezw.sync.ImageRefsResponse
import org.syezw.sync.ImageRefsUpsertRequest
import org.syezw.sync.ImageUploadRequest
import org.syezw.sync.PeriodPayload
import org.syezw.sync.PeriodSyncItem
import org.syezw.sync.PeriodMeta
import org.syezw.sync.SyncCounts
import org.syezw.sync.SyncDownloadRequest
import org.syezw.sync.SyncDownloadEnvelope
import org.syezw.sync.SyncDownloadResponse
import org.syezw.sync.SyncMeta
import org.syezw.sync.SyncMetaRequest
import org.syezw.sync.SyncMetaResponse
import org.syezw.sync.SyncUploadRequest
import org.syezw.sync.SyncUploadResponse
import org.syezw.sync.TodoPayload
import org.syezw.sync.TodoSyncItem
import org.syezw.sync.decryptFromBlob
import org.syezw.sync.deriveAesKeyFromPassphrase
import org.syezw.sync.encryptToBlob
import org.syezw.sync.sha256Hex
import org.syezw.util.normalizeDiaryImageName
import java.io.OutputStream

data class UnusedDiaryImageState(
    val isChecking: Boolean = false,
    val hasChecked: Boolean = false,
    val unusedPaths: List<String> = emptyList(),
    val usedPaths: List<String> = emptyList(),
    val scannedPaths: List<String> = emptyList(),
    val lastCheckedAt: Long? = null
)

data class SyncLogEntry(
    val timestamp: Long,
    val action: String,
    val success: Boolean,
    val message: String
)

data class SyncCountSummary(
    val diaries: Int = 0,
    val todos: Int = 0,
    val periods: Int = 0,
    val imageUploads: Int = 0,
    val imageDownloads: Int = 0
)

data class SyncProgressState(
    val inProgress: Boolean = false,
    val percent: Int = 0,
    val message: String = ""
)

class SettingsViewModel(
    private val application: Application,
    private val database: AppDatabase,
    private val dataStore: DataStore<Preferences>
) : AndroidViewModel(application) {

    private val gson = GsonBuilder().registerTypeAdapter(
        LocalDate::class.java, JsonDeserializer { json, _, _ ->
            LocalDate.parse(json.asString, DateTimeFormatter.ISO_LOCAL_DATE)
        })
        // 注册 Serializer 是一个好习惯，尽管此 ViewModel 主要用于反序列化
        .registerTypeAdapter(
            LocalDate::class.java, JsonSerializer<LocalDate> { src, _, _ ->
                JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }).create()

    private object PreferencesKeys {
        val DEFAULT_AUTHOR = stringPreferencesKey("default_author")
        val DATE_TOGETHER = stringPreferencesKey("date_together")
        val PERIOD_TRACKING_ENABLED = booleanPreferencesKey("period_tracking_enabled")
        val PERIOD_DATA = stringPreferencesKey("period_data_json")
        val LOVE_BG_IMAGE_URI = stringPreferencesKey("love_bg_image_uri")
        val LOVE_BG_ENABLED = booleanPreferencesKey("love_bg_enabled")
        val REMOTE_API_BASE_URL = stringPreferencesKey("remote_api_base_url")
        val REMOTE_API_KEY = stringPreferencesKey("remote_api_key")
        val AES_PASSPHRASE = stringPreferencesKey("aes_passphrase")
        val LAST_UPLOAD_AT = stringPreferencesKey("last_upload_at")
        val LAST_DOWNLOAD_AT = stringPreferencesKey("last_download_at")
        val LAST_UPLOAD_FAILED = booleanPreferencesKey("last_upload_failed")
        val LAST_DOWNLOAD_FAILED = booleanPreferencesKey("last_download_failed")
        val SYNC_LOGS = stringPreferencesKey("sync_logs_json")
    }
    private val minUploadIntervalMs = 30_000L
    private val maxSyncLogs = 200
    private val maxSyncLogAgeMs = 7L * 24 * 60 * 60 * 1000
    private val maxUploadBatchBytes = 512 * 1024

    private val _uploadProgress = MutableStateFlow(SyncProgressState())
    val uploadProgress = _uploadProgress.asStateFlow()

    private val _downloadProgress = MutableStateFlow(SyncProgressState())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _lastUploadSummary = MutableStateFlow<String?>(null)
    val lastUploadSummary = _lastUploadSummary.asStateFlow()

    private val _lastDownloadSummary = MutableStateFlow<String?>(null)
    val lastDownloadSummary = _lastDownloadSummary.asStateFlow()

    val defaultAuthor: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_AUTHOR] ?: "syezw"
    }

    val dateTogether: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DATE_TOGETHER] ?: "2025-04-06"
    }

    val isPeriodTrackingEnabledStateFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PERIOD_TRACKING_ENABLED] ?: false
    }

    val isPeriodTrackingEnabled = isPeriodTrackingEnabledStateFlow.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false
    )
    val periodData: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PERIOD_DATA] ?: ""
    }

    private val _unusedDiaryImageState = MutableStateFlow(UnusedDiaryImageState())
    val unusedDiaryImageState = _unusedDiaryImageState.asStateFlow()

    fun updateDefaultAuthor(newAuthor: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.DEFAULT_AUTHOR] = newAuthor
            }
        }
    }

    fun updateDate(newDate: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.DATE_TOGETHER] = newDate
            }
        }
    }

    fun setPeriodTrackingEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.PERIOD_TRACKING_ENABLED] = isEnabled
            }
        }
    }

    val loveBgImageUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOVE_BG_IMAGE_URI]
    }

    val loveBgEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOVE_BG_ENABLED] ?: false
    }

    val remoteApiBaseUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.REMOTE_API_BASE_URL] ?: ""
    }

    val remoteApiKey: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.REMOTE_API_KEY] ?: ""
    }

    val aesPassphrase: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AES_PASSPHRASE] ?: ""
    }

    val syncLogs: Flow<List<SyncLogEntry>> = dataStore.data.map { preferences ->
        val json = preferences[PreferencesKeys.SYNC_LOGS] ?: "[]"
        runCatching {
            val type = object : TypeToken<List<SyncLogEntry>>() {}.type
            gson.fromJson<List<SyncLogEntry>>(json, type)
        }.getOrDefault(emptyList())
    }

    suspend fun setLoveBgImageUri(uri: String?) {
        dataStore.edit { settings ->
            if (uri != null) {
                settings[PreferencesKeys.LOVE_BG_IMAGE_URI] = uri
            } else {
                settings.remove(PreferencesKeys.LOVE_BG_IMAGE_URI)
            }
        }
    }

    suspend fun setLoveBgEnabled(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[PreferencesKeys.LOVE_BG_ENABLED] = enabled
        }
    }

    fun updateRemoteApiBaseUrl(url: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.REMOTE_API_BASE_URL] = url
            }
        }
    }

    fun updateRemoteApiKey(key: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.REMOTE_API_KEY] = key
            }
        }
    }

    fun updateAesPassphrase(passphrase: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.AES_PASSPHRASE] = passphrase
            }
        }
    }

    fun updatePeriodData(jsonData: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.PERIOD_DATA] = jsonData
            }
        }
    }

    fun exportData() {
        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>().build()
        WorkManager.getInstance(application).enqueue(workRequest)
        Toast.makeText(application, "正在后台导出数据...", Toast.LENGTH_SHORT).show()
    }

    fun checkUnusedDiaryImages() {
        viewModelScope.launch(Dispatchers.IO) {
            _unusedDiaryImageState.value = _unusedDiaryImageState.value.copy(
                isChecking = true,
                lastCheckedAt = System.currentTimeMillis()
            )
            try {
                val usedPaths = database.diaryDao().getAllEntriesList()
                    .flatMap { it.imageUris }
                    .mapNotNull {
                        try {
                            File(resolveDiaryImagePath(it)).canonicalPath
                        } catch (e: Exception) {
                            null
                        }
                    }
                    .toSet()

                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val scanned = loadDiaryImagesFromMediaStore(downloadsDir)
                val unused = scanned.filter { it !in usedPaths }

                _unusedDiaryImageState.value = UnusedDiaryImageState(
                    isChecking = false,
                    hasChecked = true,
                    unusedPaths = unused.sorted(),
                    usedPaths = usedPaths.sorted(),
                    scannedPaths = scanned.sorted(),
                    lastCheckedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _unusedDiaryImageState.value = UnusedDiaryImageState(
                    isChecking = false,
                    hasChecked = true,
                    unusedPaths = emptyList(),
                    usedPaths = emptyList(),
                    scannedPaths = emptyList(),
                    lastCheckedAt = System.currentTimeMillis()
                )
            }
        }
    }

    private fun loadDiaryImagesFromMediaStore(downloadsDir: File): List<String> {
        val result = mutableListOf<String>()
        val relativePath = diaryImagesRelativePath()
        val resolver = application.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(relativePath)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                val rel = cursor.getString(relIdx) ?: relativePath
                val mime = cursor.getString(mimeIdx) ?: ""
                if (!mime.startsWith("image/")) continue
                val path = resolvePathFromDownloadsRelativePath(downloadsDir, rel, name)
                try {
                    result.add(path.canonicalPath)
                } catch (e: Exception) {
                    result.add(path.absolutePath)
                }
            }
        }
        if (result.isEmpty()) {
            val diaryImageDir = File(downloadsDir, DIARY_IMAGES_FOLDER)
            if (diaryImageDir.exists() && diaryImageDir.isDirectory) {
                diaryImageDir.listFiles()
                    ?.filter { it.isFile }
                    ?.mapNotNull {
                        try {
                            it.canonicalPath
                        } catch (e: Exception) {
                            null
                        }
                    }
                    ?.let { result.addAll(it) }
            }
        }
        return result
    }

    fun importData(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = application.applicationContext
            try {
                val tree = DocumentFile.fromTreeUri(context, uri)
                if (tree == null || !tree.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "无法访问选定的文件夹", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val files = tree.listFiles()

                // Helper to find latest file
                fun findLatest(prefix: String): DocumentFile? {
                    return files.filter { it.name?.startsWith(prefix) == true && it.name?.endsWith(".json") == true }
                        .maxByOrNull { it.name ?: "" }
                }

                val diaryFile = findLatest("diary_backup")
                val todoFile = findLatest("todo_backup")
                val periodFile = findLatest("period_backup")

                var importedCount = 0

                // Import Diary
                if (diaryFile != null) {
                    val json = readJson(context, diaryFile.uri)
                    if (json.isNotEmpty()) {
                        val type = object : TypeToken<List<Diary>>() {}.type
                        val diaries: List<Diary> = gson.fromJson(json, type)
                        val existing = database.diaryDao().getAllEntriesList()
                        for (item in diaries) {
                            if (existing.none { it.content == item.content && it.timestamp == item.timestamp }) {
                                database.diaryDao().insert(item.copy(id = 0))
                                importedCount++
                            }
                        }
                    }
                }

                // Import Todo
                if (todoFile != null) {
                    val json = readJson(context, todoFile.uri)
                    if (json.isNotEmpty()) {
                        val type = object : TypeToken<List<TodoTask>>() {}.type
                        val todos: List<TodoTask> = gson.fromJson(json, type)
                        val existing = database.todoTaskDao().getAllTasksList()
                        for (item in todos) {
                            if (existing.none { it.name == item.name && it.createdAt == item.createdAt }) {
                                database.todoTaskDao().insert(item.copy(id = 0))
                                importedCount++
                            }
                        }
                    }
                }

                // Import Period
                if (periodFile != null) {
                    val json = readJson(context, periodFile.uri)
                    if (json.isNotEmpty()) {
                        val type = object : TypeToken<List<PeriodRecord>>() {}.type
                        val periods: List<PeriodRecord> = gson.fromJson(json, type)
                        database.periodDao().upsertAll(periods)
                        importedCount += periods.size
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, "导入完成，共处理 $importedCount 条记录", Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun readJson(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        } ?: ""
    }

    fun syncUpload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_uploadProgress.value.inProgress) {
                    return@launch
                }
                setUploadProgress(true, 0, "准备上传")
                val prefs = dataStore.data.first()
                val lastUploadAt = prefs[PreferencesKeys.LAST_UPLOAD_AT]?.toLongOrNull() ?: 0L
                val lastUploadFailed = prefs[PreferencesKeys.LAST_UPLOAD_FAILED] ?: false
                val now = System.currentTimeMillis()
                val elapsed = now - lastUploadAt
                if (!lastUploadFailed && elapsed in 0 until minUploadIntervalMs) {
                    val waitSeconds = ((minUploadIntervalMs - elapsed) / 1000L) + 1
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            application,
                            "上传过于频繁，请 ${waitSeconds} 秒后再试",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    setUploadProgress(false, 0, "")
                    return@launch
                }

                val apiBaseUrl = remoteApiBaseUrl.first().trim()
                val apiKey = remoteApiKey.first().trim()
                val db = DbConfig(host = "", port = 0, database = "", user = "", password = "")
                val passphrase = aesPassphrase.first()
                if (apiBaseUrl.isBlank() || passphrase.isBlank() || apiKey.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "请先配置远程地址、API Key和AES密钥", Toast.LENGTH_LONG).show()
                    }
                    appendSyncLog("upload", false, "缺少远程地址或API Key或AES密钥")
                    setUploadProgress(false, 0, "")
                    return@launch
                }
                appendSyncLog("upload", true, "开始上传")
                val key = deriveAesKeyFromPassphrase(passphrase)
                setUploadProgress(true, 10, "检查差异")
                val metaResponse = fetchRemoteMeta(db, apiBaseUrl, apiKey)
                val remoteDiaryMap = metaResponse?.diaries?.associateBy({ it.uuid }, { it.updatedAt }) ?: emptyMap()
                val remoteTodoMap = metaResponse?.todos?.associateBy({ it.uuid }, { it.updatedAt }) ?: emptyMap()
                val remotePeriodMap = metaResponse?.periods?.associateBy({ it.startDate }, { it.updatedAt }) ?: emptyMap()

                val diaries = database.diaryDao().getAllEntriesList()
                    .filter { it.updatedAt > (remoteDiaryMap[it.uuid] ?: -1L) }
                    .map { diary ->
                    val payload = DiaryPayload(
                        content = diary.content,
                        tags = diary.tags,
                        location = diary.location,
                        imageUris = diary.imageUris.map(::normalizeDiaryImageName)
                    )
                    val payloadJson = gson.toJson(payload)
                    DiarySyncItem(
                        uuid = diary.uuid,
                        author = diary.author,
                        timestamp = diary.timestamp,
                        updatedAt = diary.updatedAt,
                        payload = encryptToBlob(payloadJson.toByteArray(Charsets.UTF_8), key)
                    )
                }

                val todos = database.todoTaskDao().getAllTasksList()
                    .filter { it.updatedAt > (remoteTodoMap[it.uuid] ?: -1L) }
                    .map { task ->
                    val payload = TodoPayload(name = task.name)
                    val payloadJson = gson.toJson(payload)
                    TodoSyncItem(
                        uuid = task.uuid,
                        author = task.author,
                        isCompleted = task.isCompleted,
                        createdAt = task.createdAt,
                        completedAt = task.completedAt,
                        updatedAt = task.updatedAt,
                        payload = encryptToBlob(payloadJson.toByteArray(Charsets.UTF_8), key)
                    )
                }

                val periods = database.periodDao().getAllRecords().first()
                    .filter { it.updatedAt > (remotePeriodMap[it.startDate.toString()] ?: -1L) }
                    .map { record ->
                    val payload = PeriodPayload(notes = record.notes)
                    val payloadJson = gson.toJson(payload)
                    PeriodSyncItem(
                        startDate = record.startDate.toString(),
                        endDate = record.endDate.toString(),
                        updatedAt = record.updatedAt,
                        payload = encryptToBlob(payloadJson.toByteArray(Charsets.UTF_8), key)
                    )
                }

                val images = emptyList<DiaryImageSyncItem>()

                val totalTextItems = diaries.size + todos.size + periods.size
                var processedTextItems = 0
                val client = createHttpClient()

                fun updateTextProgress() {
                    val percent = if (totalTextItems == 0) 80 else (processedTextItems * 80 / totalTextItems)
                    setUploadProgress(true, percent, "上传文本 ${processedTextItems}/${totalTextItems}")
                }

                updateTextProgress()

                val diaryBatches = chunkBySize(diaries, maxUploadBatchBytes)
                for (batch in diaryBatches) {
                    val resp = sendUploadBatch(client, apiBaseUrl, apiKey, db, batch, emptyList(), emptyList(), images)
                    if (resp == null || !resp.ok) {
                        val msg = resp?.message ?: "上传失败"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                        }
                        appendSyncLog("upload", false, msg)
                        setUploadProgress(false, 0, "")
                        return@launch
                    }
                    processedTextItems += batch.size
                    updateTextProgress()
                }

                val todoBatches = chunkBySize(todos, maxUploadBatchBytes)
                for (batch in todoBatches) {
                    val resp = sendUploadBatch(client, apiBaseUrl, apiKey, db, emptyList(), batch, emptyList(), images)
                    if (resp == null || !resp.ok) {
                        val msg = resp?.message ?: "上传失败"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                        }
                        appendSyncLog("upload", false, msg)
                        setUploadProgress(false, 0, "")
                        return@launch
                    }
                    processedTextItems += batch.size
                    updateTextProgress()
                }

                val periodBatches = chunkBySize(periods, maxUploadBatchBytes)
                for (batch in periodBatches) {
                    val resp = sendUploadBatch(client, apiBaseUrl, apiKey, db, emptyList(), emptyList(), batch, images)
                    if (resp == null || !resp.ok) {
                        val msg = resp?.message ?: "上传失败"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(application, msg, Toast.LENGTH_LONG).show()
                        }
                        appendSyncLog("upload", false, msg)
                        setUploadProgress(false, 0, "")
                        return@launch
                    }
                    processedTextItems += batch.size
                    updateTextProgress()
                }

                val imageUploadCount = syncImageUploads(db, key, apiKey) { done, total ->
                    val percent = if (total == 0) 100 else 80 + (done * 20 / total)
                    setUploadProgress(true, percent, "上传图片 ${done}/${total}")
                }
                val summary = SyncCountSummary(
                    diaries = diaries.size,
                    todos = todos.size,
                    periods = periods.size,
                    imageUploads = imageUploadCount
                )
                _lastUploadSummary.value =
                    "上传完成：diary ${summary.diaries}，todo ${summary.todos}，period ${summary.periods}，image ${summary.imageUploads}"
                appendSyncLog(
                    "upload",
                    true,
                    _lastUploadSummary.value ?: "上传完成"
                )
                dataStore.edit { settings ->
                    settings[PreferencesKeys.LAST_UPLOAD_AT] = now.toString()
                    settings[PreferencesKeys.LAST_UPLOAD_FAILED] = false
                }
                setUploadProgress(false, 100, "")
            } catch (e: Exception) {
                e.printStackTrace()
                val isTimeout = e is java.net.SocketTimeoutException
                val message = if (isTimeout) {
                    "上传超时，服务器可能已收到"
                } else {
                    "上传失败: ${e.message}"
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, message, Toast.LENGTH_LONG).show()
                }
                appendSyncLog("upload", false, message)
                dataStore.edit { settings ->
                    settings[PreferencesKeys.LAST_UPLOAD_FAILED] = true
                }
                setUploadProgress(false, 0, "")
            }
        }
    }

    fun syncDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_downloadProgress.value.inProgress) {
                    return@launch
                }
                val prefs = dataStore.data.first()
                val lastDownloadAt =
                    prefs[PreferencesKeys.LAST_DOWNLOAD_AT]?.toLongOrNull() ?: 0L
                val lastDownloadFailed = prefs[PreferencesKeys.LAST_DOWNLOAD_FAILED] ?: false
                val now = System.currentTimeMillis()
                val elapsed = now - lastDownloadAt
                if (!lastDownloadFailed && elapsed in 0 until minUploadIntervalMs) {
                    val waitSeconds = ((minUploadIntervalMs - elapsed) / 1000L) + 1
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            application,
                            "下载过于频繁，请 ${waitSeconds} 秒后再试",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    setDownloadProgress(false, 0, "")
                    return@launch
                }

                setDownloadProgress(true, 0, "准备下载")
                val apiBaseUrl = remoteApiBaseUrl.first().trim()
                val apiKey = remoteApiKey.first().trim()
                val db = DbConfig(host = "", port = 0, database = "", user = "", password = "")
                val passphrase = aesPassphrase.first()
                if (apiBaseUrl.isBlank() || passphrase.isBlank() || apiKey.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "请先配置远程地址、API Key和AES密钥", Toast.LENGTH_LONG).show()
                    }
                    appendSyncLog("download", false, "缺少远程地址或API Key或AES密钥")
                    setDownloadProgress(false, 0, "")
                    return@launch
                }
                appendSyncLog("download", true, "开始下载")
                val key = deriveAesKeyFromPassphrase(passphrase)
                setDownloadProgress(true, 10, "检查差异")
                val localDiaryMeta = database.diaryDao().getAllEntriesList()
                    .map { SyncMeta(uuid = it.uuid, updatedAt = it.updatedAt) }
                val localTodoMeta = database.todoTaskDao().getAllTasksList()
                    .map { SyncMeta(uuid = it.uuid, updatedAt = it.updatedAt) }
                val localPeriodMeta = database.periodDao().getAllRecords().first()
                    .map { PeriodMeta(startDate = it.startDate.toString(), updatedAt = it.updatedAt) }
                val requestBody = SyncDownloadRequest(
                    db = db,
                    diaries = localDiaryMeta,
                    todos = localTodoMeta,
                    periods = localPeriodMeta
                )
                val json = gson.toJson(requestBody)
                val client = createHttpClient()
                val request = Request.Builder()
                    .url("${apiBaseUrl.trimEnd('/')}/sync/download")
                    .addHeader("X-API-Key", apiKey)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()

                val responseBody = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(application, "下载失败: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                        appendSyncLog("download", false, "下载失败: ${response.code}")
                        setDownloadProgress(false, 0, "")
                        return@launch
                    }
                    response.body?.string() ?: ""
                }

                val envelope = gson.fromJson(responseBody, SyncDownloadEnvelope::class.java)
                if (!envelope.ok) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "下载失败: ${envelope.message}", Toast.LENGTH_LONG).show()
                    }
                    appendSyncLog("download", false, "下载失败: ${envelope.message}")
                    dataStore.edit { settings ->
                        settings[PreferencesKeys.LAST_DOWNLOAD_FAILED] = true
                    }
                    setDownloadProgress(false, 0, "")
                    return@launch
                }
                val response = envelope.data
                setDownloadProgress(true, 80, "处理数据")
                val decryptOk = applyDownloadedData(response, key)
                val imageDownloadCount = syncImageDownloads(db, key, apiKey) { done, total ->
                    val percent = if (total == 0) 100 else 80 + (done * 20 / total)
                    setDownloadProgress(true, percent, "下载图片 ${done}/${total}")
                }
                val summary = SyncCountSummary(
                    diaries = response.diaries.size,
                    todos = response.todos.size,
                    periods = response.periods.size,
                    imageDownloads = imageDownloadCount
                )
                _lastDownloadSummary.value =
                    "下载完成：diary ${summary.diaries}，todo ${summary.todos}，period ${summary.periods}，image ${summary.imageDownloads}"
                appendSyncLog(
                    "download",
                    true,
                    _lastDownloadSummary.value ?: "下载完成"
                )
                if (!decryptOk) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            application,
                            "下载完成，但有部分数据解密失败，请检查AES密钥是否一致",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                dataStore.edit { settings ->
                    settings[PreferencesKeys.LAST_DOWNLOAD_AT] = now.toString()
                    settings[PreferencesKeys.LAST_DOWNLOAD_FAILED] = false
                }
                setDownloadProgress(false, 100, "")
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                appendSyncLog("download", false, "下载异常: ${e.message}")
                dataStore.edit { settings ->
                    settings[PreferencesKeys.LAST_DOWNLOAD_FAILED] = true
                }
                setDownloadProgress(false, 0, "")
            }
        }
    }

    private suspend fun applyDownloadedData(
        response: SyncDownloadResponse,
        key: javax.crypto.spec.SecretKeySpec
    ): Boolean {
        var failedDiaries = 0
        var failedTodos = 0
        var failedPeriods = 0

        val localDiaries = database.diaryDao().getAllEntriesList().associateBy { it.uuid }
        for (item in response.diaries) {
            try {
                val payloadJson = String(decryptFromBlob(item.payload, key), Charsets.UTF_8)
                val payload = gson.fromJson(payloadJson, DiaryPayload::class.java)
                val existing = localDiaries[item.uuid]
                val updatedDiary = Diary(
                    id = existing?.id ?: 0,
                    uuid = item.uuid,
                    content = payload.content,
                    author = item.author,
                    tags = payload.tags,
                    timestamp = item.timestamp,
                    updatedAt = item.updatedAt,
                    location = payload.location,
                    imageUris = payload.imageUris
                )
                if (existing == null) {
                    database.diaryDao().insert(updatedDiary)
                } else if (item.updatedAt > existing.updatedAt) {
                    database.diaryDao().update(updatedDiary)
                }
            } catch (e: Exception) {
                failedDiaries += 1
                appendSyncLog("download", false, "解密日记失败: ${item.uuid} (${e.message})")
            }
        }

        val localTodos = database.todoTaskDao().getAllTasksList().associateBy { it.uuid }
        for (item in response.todos) {
            try {
                val payloadJson = String(decryptFromBlob(item.payload, key), Charsets.UTF_8)
                val payload = gson.fromJson(payloadJson, TodoPayload::class.java)
                val existing = localTodos[item.uuid]
                val updated = TodoTask(
                    id = existing?.id ?: 0,
                    uuid = item.uuid,
                    name = payload.name,
                    author = item.author,
                    isCompleted = item.isCompleted,
                    createdAt = item.createdAt,
                    completedAt = item.completedAt,
                    updatedAt = item.updatedAt
                )
                if (existing == null) {
                    database.todoTaskDao().insert(updated)
                } else if (item.updatedAt > existing.updatedAt) {
                    database.todoTaskDao().update(updated)
                }
            } catch (e: Exception) {
                failedTodos += 1
                appendSyncLog("download", false, "解密待办失败: ${item.uuid} (${e.message})")
            }
        }

        val localPeriods = database.periodDao().getAllRecords().first().associateBy { it.startDate }
        for (item in response.periods) {
            try {
                val payloadJson = String(decryptFromBlob(item.payload, key), Charsets.UTF_8)
                val payload = gson.fromJson(payloadJson, PeriodPayload::class.java)
                val startDate = LocalDate.parse(item.startDate)
                val endDate = LocalDate.parse(item.endDate)
                val existing = localPeriods[startDate]
                val updated = PeriodRecord(
                    startDate = startDate,
                    endDate = endDate,
                    notes = payload.notes,
                    updatedAt = item.updatedAt
                )
                if (existing == null) {
                    database.periodDao().upsert(updated)
                } else if (item.updatedAt > existing.updatedAt) {
                    database.periodDao().upsert(updated)
                }
            } catch (e: Exception) {
                failedPeriods += 1
                appendSyncLog("download", false, "解密周期失败: ${item.startDate} (${e.message})")
            }
        }

        if (failedDiaries + failedTodos + failedPeriods > 0) {
            appendSyncLog(
                "download",
                false,
                "部分解密失败: diary ${failedDiaries}, todo ${failedTodos}, period ${failedPeriods}"
            )
        }

        // Images are fetched on demand.
        return failedDiaries + failedTodos + failedPeriods == 0
    }

    suspend fun fetchImageFromRemote(diaryUuid: String, fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiBaseUrl = remoteApiBaseUrl.first().trim()
                val apiKey = remoteApiKey.first().trim()
                val db = DbConfig(host = "", port = 0, database = "", user = "", password = "")
                val passphrase = aesPassphrase.first()
                if (apiBaseUrl.isBlank() || passphrase.isBlank() || apiKey.isBlank()) return@withContext false
                val diaryTitle = database.diaryDao().getByUuid(diaryUuid)?.content?.take(10) ?: "unknown"
                val key = deriveAesKeyFromPassphrase(passphrase)
                val normalizedFileName = normalizeDiaryImageName(fileName)
                val requestBody = ImageFetchRequest(db = db, diaryUuid = diaryUuid, fileName = normalizedFileName)
                val json = gson.toJson(requestBody)
                val client = createHttpClient()
                val request = Request.Builder()
                    .url("${apiBaseUrl.trimEnd('/')}/images/fetch")
                    .addHeader("X-API-Key", apiKey)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
                val responseBody = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        appendSyncLog(
                            "image_download",
                            false,
                            "下载图片失败: ${response.code} (${diaryTitle}/${normalizedFileName})"
                        )
                        return@withContext false
                    }
                    response.body?.string() ?: return@withContext false
                }
                val response = gson.fromJson(responseBody, ImageFetchResponse::class.java)
                val bytes = decryptFromBlob(response.blob, key)
                val mimeType = when (response.fileName.substringAfterLast('.', "").lowercase()) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, response.fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/syezw_diary_images")
                }
                val uri = application.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return@withContext false
                application.contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                    output.write(bytes)
                }
                appendSyncLog(
                    "image_download",
                    true,
                    "下载图片成功: ${diaryTitle}/${response.fileName}"
                )
                true
            } catch (e: Exception) {
                appendSyncLog("image_download", false, "下载图片异常: ${e.message}")
                false
            }
        }
    }

    private suspend fun syncImageUploads(
        db: DbConfig,
        key: javax.crypto.spec.SecretKeySpec,
        apiKey: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        val apiBaseUrl = remoteApiBaseUrl.first().trim()
        if (apiBaseUrl.isBlank() || apiKey.isBlank()) return 0
        val client = createHttpClient()

        val hashesResponse = runCatching {
            val json = gson.toJson(db)
            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/images/hashes")
                .addHeader("X-API-Key", apiKey)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching ImageHashListResponse(emptyList())
                gson.fromJson(response.body?.string() ?: "", ImageHashListResponse::class.java)
            }
        }.getOrDefault(ImageHashListResponse(emptyList()))

        val existing = hashesResponse.hashes.toSet()
        val diaryMap = database.diaryDao().getAllEntriesList().associateBy { it.uuid }
        val imagesToUpload = mutableListOf<DiaryImageSyncItem>()
        val refsToUpsert = mutableListOf<DiaryImageRefItem>()

        for ((uuid, diary) in diaryMap) {
            for (imageNameOrPath in diary.imageUris) {
                val normalizedName = normalizeDiaryImageName(imageNameOrPath)
                val primaryFile = File(imageNameOrPath)
                val fallbackFile = resolveDiaryImageFile(normalizedName)
                val file = when {
                    primaryFile.exists() -> primaryFile
                    fallbackFile.exists() -> fallbackFile
                    else -> null
                } ?: continue

                val bytes = file.readBytes()
                val hash = sha256Hex(bytes)
                refsToUpsert.add(
                    DiaryImageRefItem(
                        diaryUuid = uuid,
                        fileName = normalizedName,
                        hash = hash,
                        updatedAt = file.lastModified()
                    )
                )
                if (!existing.contains(hash)) {
                    imagesToUpload.add(
                        DiaryImageSyncItem(
                            fileName = normalizedName,
                            diaryUuid = uuid,
                            hash = hash,
                            updatedAt = file.lastModified(),
                            blob = encryptToBlob(bytes, key)
                        )
                    )
                }
            }
        }

        var uploadedCount = 0
        val totalImages = imagesToUpload.size
        onProgress(0, totalImages)
        if (imagesToUpload.isNotEmpty()) {
            val req = ImageUploadRequest(db = db, images = imagesToUpload)
            val json = gson.toJson(req)
            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/images/upload")
                .addHeader("X-API-Key", apiKey)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
            uploadedCount = imagesToUpload.size
            onProgress(uploadedCount, totalImages)
        }

        if (refsToUpsert.isNotEmpty()) {
            val req = ImageRefsUpsertRequest(db = db, refs = refsToUpsert)
            val json = gson.toJson(req)
            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/images/refs/upsert")
                .addHeader("X-API-Key", apiKey)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
        }
        return uploadedCount
    }

    private suspend fun syncImageDownloads(
        db: DbConfig,
        key: javax.crypto.spec.SecretKeySpec,
        apiKey: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        val apiBaseUrl = remoteApiBaseUrl.first().trim()
        if (apiBaseUrl.isBlank() || apiKey.isBlank()) return 0
        val client = createHttpClient()
        val json = gson.toJson(db)
        val request = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/images/refs")
            .addHeader("X-API-Key", apiKey)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        val responseBody = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return 0
            response.body?.string() ?: return 0
        }
        val refs = gson.fromJson(responseBody, ImageRefsResponse::class.java).refs
        var downloadedCount = 0
        val total = refs.size
        var done = 0
        onProgress(done, total)
        for (ref in refs) {
            val path = resolveDiaryImagePath(ref.fileName)
            val file = File(path)
            val needsDownload = if (!file.exists()) {
                true
            } else {
                val localHash = sha256Hex(file.readBytes())
                localHash != ref.hash
            }
            if (needsDownload) {
                if (fetchImageFromRemote(ref.diaryUuid, ref.fileName)) {
                    downloadedCount += 1
                }
            }
            done += 1
            onProgress(done, total)
        }
        return downloadedCount
    }

    private suspend fun loadDbConfig(): DbConfig {
        return DbConfig(host = "", port = 0, database = "", user = "", password = "")
    }

    private fun setUploadProgress(inProgress: Boolean, percent: Int, message: String) {
        _uploadProgress.value = SyncProgressState(inProgress, percent.coerceIn(0, 100), message)
    }

    private fun setDownloadProgress(inProgress: Boolean, percent: Int, message: String) {
        _downloadProgress.value = SyncProgressState(inProgress, percent.coerceIn(0, 100), message)
    }

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private fun <T> chunkBySize(items: List<T>, maxBytes: Int): List<List<T>> {
        if (items.isEmpty()) return emptyList()
        val batches = mutableListOf<MutableList<T>>()
        var current = mutableListOf<T>()
        var currentBytes = 0
        for (item in items) {
            val size = gson.toJson(item).toByteArray(Charsets.UTF_8).size
            if (current.isNotEmpty() && currentBytes + size > maxBytes) {
                batches.add(current)
                current = mutableListOf()
                currentBytes = 0
            }
            current.add(item)
            currentBytes += size
        }
        if (current.isNotEmpty()) {
            batches.add(current)
        }
        return batches
    }

    private fun sendUploadBatch(
        client: OkHttpClient,
        apiBaseUrl: String,
        apiKey: String,
        db: DbConfig,
        diaries: List<DiarySyncItem>,
        todos: List<TodoSyncItem>,
        periods: List<PeriodSyncItem>,
        images: List<DiaryImageSyncItem>
    ): SyncUploadResponse? {
        val requestBody = SyncUploadRequest(
            db = db,
            diaries = diaries,
            todos = todos,
            periods = periods,
            images = images
        )
        val json = gson.toJson(requestBody)
        val request = Request.Builder()
            .url("${apiBaseUrl.trimEnd('/')}/sync/upload")
            .addHeader("X-API-Key", apiKey)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val parsed = runCatching { gson.fromJson(body, SyncUploadResponse::class.java) }.getOrNull()
            if (!response.isSuccessful) {
                parsed ?: SyncUploadResponse(
                    ok = false,
                    message = "上传失败: ${response.code}",
                    counts = SyncCounts(0, 0, 0, 0)
                )
            } else {
                parsed ?: SyncUploadResponse(
                    ok = true,
                    message = "ok",
                    counts = SyncCounts(diaries.size, todos.size, periods.size, images.size)
                )
            }
        }
    }

    private suspend fun appendSyncLog(action: String, success: Boolean, message: String) {
        val now = System.currentTimeMillis()
        dataStore.edit { settings ->
            val json = settings[PreferencesKeys.SYNC_LOGS] ?: "[]"
            val type = object : TypeToken<List<SyncLogEntry>>() {}.type
            val current = runCatching { gson.fromJson<List<SyncLogEntry>>(json, type) }
                .getOrDefault(emptyList())
            val filtered = current.filter { now - it.timestamp <= maxSyncLogAgeMs }.toMutableList()
            filtered.add(SyncLogEntry(now, action, success, message))
            val trimmed = if (filtered.size > maxSyncLogs) {
                filtered.takeLast(maxSyncLogs)
            } else {
                filtered
            }
            settings[PreferencesKeys.SYNC_LOGS] = gson.toJson(trimmed)
        }
    }

    private fun fetchRemoteMeta(
        db: DbConfig,
        apiBaseUrl: String,
        apiKey: String
    ): SyncMetaResponse? {
        return try {
            val client = createHttpClient()
            val json = gson.toJson(SyncMetaRequest(db = db))
            val request = Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/sync/meta")
                .addHeader("X-API-Key", apiKey)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                gson.fromJson(body, SyncMetaResponse::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun exportSyncLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logs = syncLogs.first()
                if (logs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "暂无同步日志可导出", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                val fileName = "syezw_sync_logs_${sdf.format(java.util.Date())}.txt"
                val builder = StringBuilder()
                val lineSdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                for (entry in logs) {
                    val ts = lineSdf.format(java.util.Date(entry.timestamp))
                    builder.append("[").append(ts).append("] ")
                        .append(entry.action).append(" ")
                        .append(if (entry.success) "OK" else "FAIL")
                        .append(" - ").append(entry.message)
                        .append("\n")
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = application.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
                if (uri == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "导出失败：无法创建文件", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                application.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(builder.toString().toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "同步日志已导出到下载目录", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

class SettingsViewModelFactory(
    private val application: Application,
    private val database: AppDatabase,
    private val dataStore: DataStore<Preferences>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SettingsViewModel(
                application,
                database,
                dataStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
