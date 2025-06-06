package org.syezw.model


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.syezw.data.Diary
import org.syezw.data.DiaryDao

data class DiaryUiState(
    val entries: List<Diary> = emptyList(),
    val selectedEntry: Diary? = null,
    // Add fields for managing input in Add/Edit screen if needed
    val currentContent: String = "",
    val currentAuthor: String = "SYEZW",
    val currentTags: List<String> = emptyList(),
    val currentTimestamp: Long = System.currentTimeMillis(),
    val currentLocation: String? = null
)

class DiaryViewModel(private val diaryDao: DiaryDao) : ViewModel() {
    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    init {
        loadAllEntries()
    }

    private fun loadAllEntries() {
        viewModelScope.launch {
            diaryDao.getAll().collect { entries ->
                _uiState.update { it.copy(entries = entries) }
            }
        }
    }

    fun getEntryById(id: Int) {
        viewModelScope.launch {
            diaryDao.getEntryById(id).collect { entry ->
                _uiState.update {
                    it.copy(
                        selectedEntry = entry,
                        currentContent = entry?.content ?: "",
                        currentTags = entry?.tags ?: emptyList(),
                        currentTimestamp = entry?.timestamp ?: System.currentTimeMillis(),
                        currentLocation = entry?.location
                    )
                }
            }
        }
    }

    fun updateContent(content: String) {
        _uiState.update { it.copy(currentContent = content) }
    }

    fun addTag(tag: String) {
        if (tag.isNotBlank() && !_uiState.value.currentTags.contains(tag)) {
            _uiState.update { it.copy(currentTags = it.currentTags + tag) }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(currentTags = it.currentTags - tag) }
    }

    fun updateTimestamp(timestamp: Long) {
        _uiState.update { it.copy(currentTimestamp = timestamp) }
    }

    fun updateLocation(location: String?) {
        _uiState.update { it.copy(currentLocation = location) }
    }

    fun saveDiaryEntry() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.currentContent.isBlank() || currentState.currentTags.isEmpty()) {
                // Handle validation error (e.g., show a snackbar)
                return@launch
            }

            val entryToSave = currentState.selectedEntry?.copy(
                content = currentState.currentContent,
                tags = currentState.currentTags,
                timestamp = currentState.currentTimestamp,
                location = currentState.currentLocation
            ) ?: Diary(
                content = currentState.currentContent,
                tags = currentState.currentTags,
                timestamp = currentState.currentTimestamp,
                location = currentState.currentLocation
            )

            if (entryToSave.id == 0) { // New entry
                diaryDao.insert(entryToSave)
            } else { // Existing entry
                diaryDao.update(entryToSave)
            }
            // Optionally clear input fields or navigate back
            clearInputFields()
        }
    }

    fun deleteEntry(entry: Diary) {
        viewModelScope.launch {
            diaryDao.delete(entry)
        }
    }

    fun clearInputFields() {
        _uiState.update {
            it.copy(
                selectedEntry = null,
                currentContent = "",
                currentTags = emptyList(),
                currentTimestamp = System.currentTimeMillis(),
                currentLocation = null
            )
        }
    }
}

class DiaryViewModelFactory(private val diaryDao: DiaryDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return DiaryViewModel(diaryDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}