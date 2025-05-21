package org.syezw.model

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.syezw.preference.SettingsManager
import org.syezw.data.TodoTask
import org.syezw.data.TodoTaskDao
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.reflect.Type

data class TodoUiState(
    val tasks: List<TodoTask> = emptyList(),
    val selectedTask: TodoTask? = null,
    val currentName: String = "",
    val currentAuthor: String = SettingsManager.DEFAULT_AUTHOR_VALUE,
    val currentIsCompleted: Boolean = false, // Added for completeness in dialog
    val currentCreatedAt: Long = System.currentTimeMillis(), // Default to now for new tasks
    val currentCompletedAt: Long? = null
)

class TodoViewModel(
    private val todoTaskDao: TodoTaskDao, private val settingsManager: SettingsManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()
    private val gson = Gson()

    init {
        loadAllTasks()
        viewModelScope.launch {
            val author = settingsManager.defaultAuthorFlow.first()
            _uiState.update { it.copy(currentAuthor = author) }
        }
    }

    private fun loadAllTasks() {
        viewModelScope.launch {
            todoTaskDao.getAll().collect { tasks -> // Using getAll() from your Dao
                _uiState.update { it.copy(tasks = tasks) }
            }
        }
    }

    // --- EXPORT FUNCTION ---
    fun exportTodosToJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Using getAllTasksList() which you already have
                val tasksToExport = todoTaskDao.getAllTasksList()

                if (tasksToExport.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No tasks to export.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val jsonString = gson.toJson(tasksToExport)

                withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(uri, "w")
                        ?.use { parcelFileDescriptor ->
                            FileOutputStream(parcelFileDescriptor.fileDescriptor).use { fileOutputStream ->
                                fileOutputStream.write(jsonString.toByteArray())
                            }
                        }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Tasks exported successfully!", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- IMPORT FUNCTION ---
    fun importTodosFromJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonString = StringBuilder()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                jsonString.append(line)
                            }
                        }
                    }
                }

                if (jsonString.isBlank()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import file is empty.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val listType: Type = object : TypeToken<List<TodoTask>>() {}.type
                val importedTasks: List<TodoTask> = gson.fromJson(jsonString.toString(), listType)

                if (importedTasks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context, "No tasks found in the import file.", Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val existingTasks = todoTaskDao.getAllTasksList()
                var newTasksCount = 0

                for (importedTask in importedTasks) {
                    // Simple duplicate check: by name and createdAt timestamp.
                    // This assumes that if a task with the same name was created at the exact same millisecond,
                    // it's a duplicate. You might want a more robust strategy if creation times can clash
                    // or if names are not unique enough.
                    val isDuplicate = existingTasks.any { existing ->
                        existing.name == importedTask.name && existing.createdAt == importedTask.createdAt
                    }

                    if (!isDuplicate) {
                        // Insert as a new task, clearing the ID so Room generates a new one.
                        // Ensure all fields from the importedTask are correctly copied.
                        todoTaskDao.insert(
                            importedTask.copy(
                                id = 0, // Let Room generate new ID
                                isCompleted = importedTask.isCompleted, // Preserve completion status
                                completedAt = if (importedTask.isCompleted) importedTask.completedAt
                                    ?: System.currentTimeMillis() else null
                                // If imported as completed but no completedAt, set to now or preserve null based on your logic
                            )
                        )
                        newTasksCount++
                    }
                    // Optional: Add logic here to update existing tasks if a match is found based on a unique ID (not 'id' from JSON)
                }

                withContext(Dispatchers.Main) {
                    if (newTasksCount > 0) {
                        Toast.makeText(
                            context,
                            "$newTasksCount new tasks imported and merged!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "No new tasks to import or all tasks were duplicates.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- ViewModel methods for CRUD operations and UI state management ---
    fun addTask(name: String) { // Simplified based on new fields
        if (name.isBlank()) {
            // Consider showing an error Toast or some UI feedback
            return
        }
        viewModelScope.launch {
            val authorToUse = settingsManager.defaultAuthorFlow.first()
            val newTask = TodoTask(
                name = name,
                author = authorToUse,
                createdAt = System.currentTimeMillis(),
                isCompleted = false,
                completedAt = null
            )
            todoTaskDao.insert(newTask)
            clearInputFields()
        }
    }

    fun updateTask(task: TodoTask) {
        viewModelScope.launch {
            // If the task is being marked as completed, set completedAt.
            // If it's being marked as incomplete, clear completedAt.
            val taskToUpdate = task.copy(
                completedAt = if (task.isCompleted && task.completedAt == null) System.currentTimeMillis()
                else if (!task.isCompleted) null
                else task.completedAt
            )
            todoTaskDao.update(taskToUpdate)
            clearInputFields()
        }
    }

    fun toggleCompletion(task: TodoTask) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
            )
            todoTaskDao.update(updatedTask)
            // No need to clear input fields here typically, as it's a direct list item interaction
        }
    }

    fun deleteTask(task: TodoTask) {
        viewModelScope.launch {
            todoTaskDao.delete(task)
        }
    }

    fun selectTask(task: TodoTask?) { // For editing
        _uiState.update {
            it.copy(
                selectedTask = task,
                currentName = task?.name ?: "",
                currentIsCompleted = task?.isCompleted ?: false,
                currentCreatedAt = task?.createdAt
                    ?: System.currentTimeMillis(), // Keep original createdAt for edits
                currentCompletedAt = task?.completedAt
            )
        }
    }

    // Methods to update current input fields for the dialog
    fun updateCurrentName(name: String) {
        _uiState.update { it.copy(currentName = name) }
    }
    // You might not need to update currentIsCompleted or currentCreatedAt directly from dialog
    // as completion is usually a toggle and createdAt is fixed.

    fun clearInputFields() { // Reset for new task entry
        viewModelScope.launch { // Launch a coroutine
            _uiState.update {
                val currentDefaultAuthor =
                    settingsManager.defaultAuthorFlow.first() // Call suspend function
                it.copy(
                    selectedTask = null,
                    currentName = "",
                    currentAuthor = currentDefaultAuthor, // Use the fetched author
                    currentIsCompleted = false,
                    currentCreatedAt = System.currentTimeMillis(),
                    currentCompletedAt = null
                )
            }
        }
    }
}

class TodoViewModelFactory(
    private val todoTaskDao: TodoTaskDao, private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return TodoViewModel(todoTaskDao, settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}