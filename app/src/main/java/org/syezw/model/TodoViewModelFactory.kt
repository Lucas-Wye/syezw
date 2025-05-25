package org.syezw.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.syezw.data.TodoTaskDao

class TodoViewModelFactory(private val todoTaskDao: TodoTaskDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(todoTaskDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}