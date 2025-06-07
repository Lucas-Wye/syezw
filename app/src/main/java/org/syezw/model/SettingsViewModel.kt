package org.syezw.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.syezw.data.SettingsManager
import org.syezw.dataStore

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application.dataStore)

    val defaultAuthor: StateFlow<String> = settingsManager.defaultAuthorFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsManager.DEFAULT_AUTHOR_VALUE
        )

    val dateFormat: StateFlow<String> = settingsManager.dateFormatFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsManager.DEFAULT_DATE_FORMAT_VALUE
        )

    fun updateDefaultAuthor(newAuthor: String) {
        viewModelScope.launch {
            settingsManager.setDefaultAuthor(newAuthor)
        }
    }

    fun updateDateFormat(newFormat: String) {
        viewModelScope.launch {
            settingsManager.setDateFormat(newFormat)
        }
    }
}

class SettingsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}