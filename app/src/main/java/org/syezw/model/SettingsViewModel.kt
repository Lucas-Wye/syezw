package org.syezw.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.syezw.preference.SettingsManager
import org.syezw.dataStore


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {

    private object PreferencesKeys {
        val DEFAULT_AUTHOR = stringPreferencesKey("default_author")
        val DATE_TOGETHER = stringPreferencesKey("date_together")
        // 1. 为周期记录功能创建一个新的 Preferences Key
        val PERIOD_TRACKING_ENABLED = booleanPreferencesKey("period_tracking_enabled")
        val PERIOD_DATA = stringPreferencesKey("period_data_json")
    }

    val defaultAuthor: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_AUTHOR] ?: "syezw"
    }

    val dateTogether: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DATE_TOGETHER] ?: "2025-04-06"
    }

    // 2. 创建一个 Flow 来读取周期记录的设置状态
    // 它会从 DataStore 中读取布尔值，如果不存在，则默认为 false（关闭）
    val isPeriodTrackingEnabledStateFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PERIOD_TRACKING_ENABLED] ?: false
    }

    // 将 Flow 转换为 StateFlow 以便在 Composable 中更方便地使用
    val isPeriodTrackingEnabled = isPeriodTrackingEnabledStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
    val periodData: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PERIOD_DATA] ?: ""
    }

    fun updateDefaultAuthor(newAuthor: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.DEFAULT_AUTHOR] = newAuthor
            }
        }
    }

    fun updateDate(newDate: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.DATE_TOGETHER] = newDate
            }
        }
    }

    // 3. 创建一个函数来更新周期记录的设置状态
    // 当用户在 SettingsScreen 中点击开关时，将调用此函数
    fun setPeriodTrackingEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.PERIOD_TRACKING_ENABLED] = isEnabled
            }
        }
    }

    fun updatePeriodData(jsonData: String) {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[PreferencesKeys.PERIOD_DATA] = jsonData
            }
        }
    }
}

class SettingsViewModelFactory(private val dataStore: DataStore<Preferences>) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}