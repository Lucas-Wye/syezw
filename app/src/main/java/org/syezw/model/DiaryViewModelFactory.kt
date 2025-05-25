package org.syezw.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.syezw.data.DiaryDao

class DiaryViewModelFactory(private val diaryDao: DiaryDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DiaryViewModel(diaryDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
