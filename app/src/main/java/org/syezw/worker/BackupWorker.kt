package org.syezw.worker

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.syezw.data.AppDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(applicationContext)

            val gson = GsonBuilder()
                .registerTypeAdapter(
                    LocalDate::class.java,
                    JsonSerializer<LocalDate> { src, _, _ ->
                        // 将 LocalDate 对象转换为 "YYYY-MM-DD" 格式的字符串
                        JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                )
                .create()

            // Generate timestamp for this backup session
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            // 1. Backup Diary (这个使用的是默认 Gson，如果它不包含特殊类型则没问题)
            val diaries = database.diaryDao().getAllEntriesList()
            // 注意：如果 Diary 类也包含 LocalDate，也需要使用上面配置好的 gson 实例
            val diaryJson = Gson().toJson(diaries) // 保持原样，除非它也有 LocalDate
            saveFile("diary_backup_$timestamp.json", diaryJson)

            // 2. Backup Todo (同上)
            val todos = database.todoTaskDao().getAllTasksList()
            val todoJson = Gson().toJson(todos) // 保持原样，除非它也有 LocalDate
            saveFile("todo_backup_$timestamp.json", todoJson)

            // 3. Backup Period
            val periods = database.periodDao().getAllRecords().first()
            val periodJson = gson.toJson(periods) // 使用配置好的 gson 实例
            saveFile("period_backup_$timestamp.json", periodJson)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private suspend fun saveFile(fileName: String, content: String) = withContext(Dispatchers.IO) {
        // 为了兼容 Android 10+ 的分区存储，推荐使用 MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/SyezwBackups"
            )
        }

        val resolver = applicationContext.contentResolver
        // 在 Android 10 (Q) 及以上，insert 操作可能会比较慢，确保在 IO 线程
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
            } catch (e: Exception) {
                // 如果写入失败，可以考虑删除这个不完整的 uri 条目
                e.printStackTrace()
                resolver.delete(it, null, null)
            }
        }
    }
}
