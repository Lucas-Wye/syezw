package org.syezw.model

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

data class Task(
    val id: Int,
    val user: String = "syezw",
    val name: String,
    var isCompleted: Boolean = false
)

class TodoViewModel() : ViewModel() {
    private val _tasks = mutableStateListOf<Task>()
    val tasks: List<Task> get() = _tasks

    fun addTask(name: String) {
        if (name.isNotBlank()) {
            _tasks.add(Task(id = _tasks.size + 1, name = name))
        }
    }

    fun removeTask(task: Task) {
        _tasks.removeIf { it.id == task.id }
    }

    fun toggleTaskCompletion(task: Task) {
        val index = _tasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            _tasks[index] = _tasks[index].copy(isCompleted = !_tasks[index].isCompleted)
        }
    }
}