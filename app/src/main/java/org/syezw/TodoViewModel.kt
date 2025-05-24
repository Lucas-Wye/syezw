package org.syezw

import androidx.compose.runtime.derivedStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf

class TodoViewModel() : ViewModel() {
    private val _tasks = mutableStateListOf<Task>()
    val tasks: List<Task> = _tasks

    fun addTask(name: String) {
        _tasks.add(Task(id = _tasks.size + 1, name = name))
    }

    fun removeTask(task: Task) {
        _tasks.remove(task)
    }

    fun toggleTaskCompletion(task: Task) {
        val index = _tasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            val updatedTask = _tasks[index].copy(isCompleted = !_tasks[index].isCompleted)
            _tasks[index] = updatedTask  // 触发 recomposition
        }
    }
}