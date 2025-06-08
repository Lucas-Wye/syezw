package org.syezw.preference

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
        val DATE_KEY = stringPreferencesKey("date_together")

        const val DEFAULT_AUTHOR_VALUE = "SYEZW" // Your original default
        const val DEFAULT_DATE_VALUE = "2025-04-06" // Your original date
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

    // Flow for the date
    val dateFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[DATE_KEY] ?: DEFAULT_DATE_VALUE
    }

    // Function to save the date
    suspend fun setDate(date: String) {
        dataStore.edit { settings ->
            settings[DATE_KEY] = date
        }
    }
}