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

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Create the DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_settings")

class SettingsDataStore(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")
        val ACCENT_THEME_KEY = stringPreferencesKey("accent_theme") // "DYNAMIC", "PURPLE", "CYAN", "SUNSET", "EMERALD", "GOLD"
        val PURE_BLACK_KEY = booleanPreferencesKey("pure_black_enabled")
        val FILTER_SHORT_AUDIO_KEY = booleanPreferencesKey("filter_short_audio")
        val PLAYBACK_SPEED_KEY = floatPreferencesKey("playback_speed")
        val VISUALIZER_ENABLED_KEY = booleanPreferencesKey("visualizer_enabled")
    }

    // Flow to read the dark theme preference
    val darkThemeFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[DARK_THEME_KEY] ?: true
        }

    val accentThemeFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[ACCENT_THEME_KEY] ?: "DYNAMIC"
        }

    val pureBlackFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PURE_BLACK_KEY] ?: false
        }

    val filterShortAudioFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[FILTER_SHORT_AUDIO_KEY] ?: false
        }

    val playbackSpeedFlow: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PLAYBACK_SPEED_KEY] ?: 1.0f
        }

    val visualizerEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[VISUALIZER_ENABLED_KEY] ?: true
        }

    // Suspend functions to save preferences
    suspend fun saveThemePreference(isDarkTheme: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = isDarkTheme
        }
    }

    suspend fun saveAccentTheme(accent: String) {
        dataStore.edit { preferences ->
            preferences[ACCENT_THEME_KEY] = accent
        }
    }

    suspend fun savePureBlack(isPureBlack: Boolean) {
        dataStore.edit { preferences ->
            preferences[PURE_BLACK_KEY] = isPureBlack
        }
    }

    suspend fun saveFilterShortAudio(filter: Boolean) {
        dataStore.edit { preferences ->
            preferences[FILTER_SHORT_AUDIO_KEY] = filter
        }
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED_KEY] = speed
        }
    }

    suspend fun saveVisualizerEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VISUALIZER_ENABLED_KEY] = enabled
        }
    }
}