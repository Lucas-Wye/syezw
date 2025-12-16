package org.syezw.model

import android.app.Application
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.syezw.data.AppDatabase
import org.syezw.data.Diary
import org.syezw.data.TodoTask
import org.syezw.worker.BackupWorker
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter


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
    }

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