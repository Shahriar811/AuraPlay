package com.example.auraplay

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Create the DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_settings")

class SettingsDataStore(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        // Create a key for our dark theme boolean
        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")
    }

    // Flow to read the dark theme preference
    val darkThemeFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            // Handle IO exceptions
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            // Read the boolean value, defaulting to 'true' (dark theme) if it's not set
            preferences[DARK_THEME_KEY] ?: true
        }

    // Suspend function to save the theme preference
    suspend fun saveThemePreference(isDarkTheme: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = isDarkTheme
        }
    }
}