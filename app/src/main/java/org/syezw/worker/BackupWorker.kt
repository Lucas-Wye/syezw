package org.syezw.worker

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.syezw.data.AppDatabase
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
            val gson = Gson()
            
            // Generate timestamp for this backup session
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            // 1. Backup Diary
            val diaries = database.diaryDao().getAllEntriesList()
            val diaryJson = gson.toJson(diaries)
            saveFile("diary_backup_$timestamp.json", diaryJson)

            // 2. Backup Todo
            val todos = database.todoTaskDao().getAllTasksList()
            val todoJson = gson.toJson(todos)
            saveFile("todo_backup_$timestamp.json", todoJson)

            // 3. Backup Period
            val periods = database.periodDao().getAllRecords().first()
            val periodJson = gson.toJson(periods)
            saveFile("period_backup_$timestamp.json", periodJson)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun saveFile(fileName: String, content: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/SyezwBackups")
        }

        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
        }
    }
}
