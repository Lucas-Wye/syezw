package org.syezw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Define a top-level property for the DataStore instance
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val dataStore: DataStore<Preferences>) {
    companion object {
        val DEFAULT_AUTHOR_KEY = stringPreferencesKey("default_author")
        val DATE_FORMAT_KEY = stringPreferencesKey("date_format_string")

        const val DEFAULT_AUTHOR_VALUE = "SYEZW" // Your original default
        const val DEFAULT_DATE_FORMAT_VALUE = "yyyy-MM-dd HH:mm" // Your original format
    }

    // Flow for the default author
    val defaultAuthorFlow: Flow<String> = dataStore.data.map { preferences ->
            preferences[DEFAULT_AUTHOR_KEY] ?: DEFAULT_AUTHOR_VALUE
        }

    // Function to save the default author
    suspend fun setDefaultAuthor(author: String) {
        dataStore.edit { settings ->
            settings[DEFAULT_AUTHOR_KEY] = author
        }
    }

    // Flow for the date format
    val dateFormatFlow: Flow<String> = dataStore.data.map { preferences ->
            preferences[DATE_FORMAT_KEY] ?: DEFAULT_DATE_FORMAT_VALUE
        }

    // Function to save the date format
    suspend fun setDateFormat(format: String) {
        dataStore.edit { settings ->
            settings[DATE_FORMAT_KEY] = format
        }
    }
}