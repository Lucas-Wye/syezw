package org.syezw.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.syezw.data.Diary
import org.syezw.data.DiaryDao

class DiaryViewModel(private val diaryDao: DiaryDao) : ViewModel() {
    // MutableStateFlow 用于存储数据列表，初始值为空列表
    private val _diaries = MutableStateFlow<List<Diary>>(emptyList())

    // StateFlow 只读视图，暴露给外部使用
    val diaries: StateFlow<List<Diary>> get() = _diaries

    init {
        viewModelScope.launch {
            diaryDao.getAll().collect { list ->
                _diaries.value = list
            }
        }
    }

    fun addDiary(author:String, content: String) {
        viewModelScope.launch {
            if (content.isNotBlank()) {
                val now = System.currentTimeMillis()
                val _given_diary = Diary(author = author, content = content, createdAt = now)
                diaryDao.insert(_given_diary)
            }
        }
    }

    fun removeDiary(diary: Diary) {
        viewModelScope.launch {
            val _given_diary = Diary(id = diary.id, author = diary.author, content = diary.content, createdAt = diary.createdAt)
            diaryDao.delete(_given_diary)
        }
    }
}