package org.syezw

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf

class TodoViewModel(): ViewModel() {
        private val _tasks = mutableStateListOf<Task>()
        val tasks: List<Task> = _tasks

        fun addTask(name: String) {
            val newTask = Task(id = _tasks.size + 1, name = name)
            _tasks.add(newTask)
        }

        fun removeTask(task: Task) {
            _tasks.remove(task)
        }

        fun toggleTaskCompletion(task: Task) {
            task.isCompleted = !task.isCompleted
        }
    }