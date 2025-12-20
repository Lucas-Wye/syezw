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
import org.syezw.data.Diary
import org.syezw.data.DiaryDao
import org.syezw.preference.SettingsManager
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.reflect.Type

data class DiaryUiState(
    val entries: List<Diary> = emptyList(),
    val selectedEntry: Diary? = null,
    // Add fields for managing input in Add/Edit screen if needed
    val currentContent: String = "",
    val currentAuthor: String = SettingsManager.DEFAULT_AUTHOR_VALUE,
    val currentTags: List<String> = emptyList(),
    val currentTimestamp: Long = System.currentTimeMillis(),
    val currentLocation: String? = null,
    // 筛选相关状态
    val allEntries: List<Diary> = emptyList(), // 未筛选的日记
    val selectedFilterTag: String? = null, // 当前选中的标签筛选
    val selectedFilterAuthor: String? = null, // 当前选中的作者筛选
    val availableTags: List<String> = emptyList(), // 所有可用的标签
    val availableAuthors: List<String> = emptyList(), // 所有可用的作者
    val availableLocations: List<String> = emptyList(), // 所有可用的地点
    val searchQuery: String = "" // 搜索查询文本
)

class DiaryViewModel(
    private val diaryDao: DiaryDao, private val settingsManager: SettingsManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()
    private val gson = Gson()

    init {
        loadAllEntries()
        // Update uiState with the initial author from settings
        viewModelScope.launch {
            val author = settingsManager.defaultAuthorFlow.first()
            _uiState.update { it.copy(currentAuthor = author) }
        }
    }

    fun exportDiariesToJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val diariesToExport =
                    diaryDao.getAllEntriesList()

                if (diariesToExport.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No diaries to export.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val jsonString = gson.toJson(diariesToExport)

                withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(uri, "w")
                        ?.use { parcelFileDescriptor ->
                            FileOutputStream(parcelFileDescriptor.fileDescriptor).use { fileOutputStream ->
                                fileOutputStream.write(jsonString.toByteArray())
                            }
                        }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Diaries exported successfully!", Toast.LENGTH_SHORT)
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

    fun importDiariesFromJson(context: Context, uri: Uri) {
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

                val listType: Type = object : TypeToken<List<Diary>>() {}.type
                val importedDiaries: List<Diary> = gson.fromJson(jsonString.toString(), listType)

                if (importedDiaries.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context, "No diaries found in the import file.", Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                // --- Merge Logic ---
                // For each imported diary, check if an entry with the same content and timestamp
                // (or a unique combination of fields) already exists.
                // This is a simple example; you might need more sophisticated duplicate detection.
                val existingDiaries = diaryDao.getAllEntriesList() // Get current diaries
                var newEntriesCount = 0
                var updatedEntriesCount = 0 // If you decide to update instead of just skip

                for (importedDiary in importedDiaries) {
                    // Example: check by content and timestamp.
                    // You might want to use a more robust unique identifier if available from the source,
                    // or define "sameness" based on your needs.
                    // If your Diary entity has a unique 'uuid' field that's generated on creation
                    // and included in the export, that would be ideal for matching.
                    // For now, let's assume if content AND timestamp match, it's a duplicate.
                    // Note: IDs from the JSON might clash if they are auto-generated.
                    // It's safer to insert them as new entries and let Room generate new IDs,
                    // UNLESS the IDs are globally unique (like UUIDs) and you want to preserve them.

                    val isDuplicate = existingDiaries.any { existing ->
                        existing.content == importedDiary.content && existing.timestamp == importedDiary.timestamp
                        // Add other fields for duplicate check if necessary, e.g., existing.location == importedDiary.location
                    }

                    if (!isDuplicate) {
                        // Insert as a new entry, clearing the ID so Room generates a new one.
                        diaryDao.insert(importedDiary.copy(id = 0))
                        newEntriesCount++
                    }
                    // Optional: If you want to update existing entries based on some criteria
                    // else {
                    //     val existingEntryToUpdate = existingDiaries.find { ... }
                    //     existingEntryToUpdate?.let {
                    //         diaryDao.update(it.copy(tags = importedDiary.tags, ...)) // Merge fields
                    //         updatedEntriesCount++
                    //     }
                    // }
                }

                withContext(Dispatchers.Main) {
                    if (newEntriesCount > 0) {
                        Toast.makeText(
                            context,
                            "$newEntriesCount new diaries imported and merged!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "No new diaries to import or all entries were duplicates.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    // loadAllEntries() will be called automatically due to the Flow from DAO
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadAllEntries() {
        viewModelScope.launch {
            diaryDao.getAll().collect { entries ->
                // 提取所有标签、作者和地点
                val allTags = entries.flatMap { it.tags }.distinct().sorted()
                val allAuthors = entries.map { it.author }.distinct().sorted()
                val allLocations = entries.mapNotNull { it.location }.distinct().sorted()

                _uiState.update { currentState ->
                    val filteredEntries = applyFilters(
                        entries,
                        currentState.selectedFilterTag,
                        currentState.selectedFilterAuthor,
                        currentState.searchQuery
                    )
                    currentState.copy(
                        entries = filteredEntries,
                        allEntries = entries,
                        availableTags = allTags,
                        availableAuthors = allAuthors,
                        availableLocations = allLocations
                    )
                }
            }
        }
    }

    private fun applyFilters(
        entries: List<Diary>,
        filterTag: String?,
        filterAuthor: String?,
        searchQuery: String
    ): List<Diary> {
        var filtered = entries

        // 应用标签筛选
        if (filterTag != null) {
            filtered = filtered.filter { it.tags.contains(filterTag) }
        }

        // 应用作者筛选
        if (filterAuthor != null) {
            filtered = filtered.filter { it.author == filterAuthor }
        }

        // 应用搜索查询（搜索内容、标签或地点）
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter { diary ->
                diary.content.lowercase().contains(query) ||
                        diary.tags.any { it.lowercase().contains(query) } ||
                        diary.location?.lowercase()?.contains(query) == true
            }
        }

        return filtered
    }

    fun setFilterTag(tag: String?) {
        _uiState.update { currentState ->
            val filteredEntries = applyFilters(
                currentState.allEntries,
                tag,
                currentState.selectedFilterAuthor,
                currentState.searchQuery
            )
            currentState.copy(
                selectedFilterTag = tag,
                entries = filteredEntries
            )
        }
    }

    fun setFilterAuthor(author: String?) {
        _uiState.update { currentState ->
            val filteredEntries = applyFilters(
                currentState.allEntries,
                currentState.selectedFilterTag,
                author,
                currentState.searchQuery
            )
            currentState.copy(
                selectedFilterAuthor = author,
                entries = filteredEntries
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { currentState ->
            val filteredEntries = applyFilters(
                currentState.allEntries,
                currentState.selectedFilterTag,
                currentState.selectedFilterAuthor,
                query
            )
            currentState.copy(
                searchQuery = query,
                entries = filteredEntries
            )
        }
    }

    fun clearFilters() {
        _uiState.update { currentState ->
            currentState.copy(
                selectedFilterTag = null,
                selectedFilterAuthor = null,
                searchQuery = "",
                entries = currentState.allEntries
            )
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

            val authorToUse = if (currentState.selectedEntry == null) {
                settingsManager.defaultAuthorFlow.first() // Get latest author for new entries
            } else {
                currentState.selectedEntry.author // Keep existing author for edited entries (or make this configurable too)
            }

            val entryToSave = currentState.selectedEntry?.copy(
                content = currentState.currentContent,
                author = authorToUse,
                tags = currentState.currentTags,
                timestamp = currentState.currentTimestamp,
                location = currentState.currentLocation
            ) ?: Diary(
                content = currentState.currentContent,
                author = authorToUse,
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
        viewModelScope.launch { // Launch a coroutine
            val currentDefaultAuthor =
                settingsManager.defaultAuthorFlow.first() // Call suspend function
            _uiState.update {
                it.copy(
                    selectedEntry = null,
                    currentContent = "",
                    currentAuthor = currentDefaultAuthor, // Use the fetched author
                    currentTags = emptyList(),
                    currentTimestamp = System.currentTimeMillis(),
                    currentLocation = null
                )
            }
        }
    }
}

class DiaryViewModelFactory(
    private val diaryDao: DiaryDao, private val settingsManager: SettingsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return DiaryViewModel(
                diaryDao,
                settingsManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}