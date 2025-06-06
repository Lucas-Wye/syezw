package org.syezw.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.syezw.data.TodoTask
import org.syezw.data.TodoTaskDao

class TodoViewModel(private val todoTaskDao: TodoTaskDao) : ViewModel() {
    // MutableStateFlow 用于存储数据列表，初始值为空列表
    private val _tasks = MutableStateFlow<List<TodoTask>>(emptyList())

    // StateFlow 只读视图，暴露给外部使用
    val tasks: StateFlow<List<TodoTask>> get() = _tasks

    init {
        viewModelScope.launch {
            todoTaskDao.getAll().collect { list ->
                _tasks.value = list
            }
        }
    }

    fun addTask(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val now = System.currentTimeMillis()
                val _given_task = TodoTask(name = name, createdAt = now, completedAt = null)
                todoTaskDao.insert(_given_task)
            }
        }
    }

    fun removeTask(task: TodoTask) {
        viewModelScope.launch {
            val _given_task = TodoTask(
                id = task.id,
                name = task.name,
                isCompleted = task.isCompleted,
                createdAt = task.createdAt,
                completedAt = task.completedAt
            )
            todoTaskDao.delete(_given_task)
        }
    }

    fun toggleTaskCompletion(task: TodoTask) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val _given_task = TodoTask(
                id = task.id,
                name = task.name,
                isCompleted = !task.isCompleted,
                createdAt = task.createdAt,
                completedAt = now
            )
            todoTaskDao.update(_given_task)
        }
    }
}

class TodoViewModelFactory(private val todoTaskDao: TodoTaskDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return TodoViewModel(todoTaskDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}