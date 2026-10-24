package com.example.auraplay

import com.example.auraplay.features.FeatureSettings
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
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
        val RESUME_KEY = booleanPreferencesKey("resume_playback")
        val CROSSFADE_KEY = intPreferencesKey("crossfade_seconds")
        val REPLAY_GAIN_KEY = stringPreferencesKey("replay_gain")
        val ONLY_FOLDERS_KEY = booleanPreferencesKey("selected_folders_only")
        val INCLUDED_KEY = stringSetPreferencesKey("included_folders")
        val EXCLUDED_KEY = stringSetPreferencesKey("excluded_folders")
        val SNAPSHOT_KEY = stringPreferencesKey("playback_snapshot")
        val EQ_KEY = stringPreferencesKey("equalizer_state")
        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme_enabled")
        val ACCENT_THEME_KEY = stringPreferencesKey("accent_theme") // "DYNAMIC", "PURPLE", "CYAN", "SUNSET", "EMERALD", "GOLD"
        val PURE_BLACK_KEY = booleanPreferencesKey("pure_black_enabled")
        val FILTER_SHORT_AUDIO_KEY = booleanPreferencesKey("filter_short_audio")
        val PLAYBACK_SPEED_KEY = floatPreferencesKey("playback_speed")
        val VISUALIZER_ENABLED_KEY = booleanPreferencesKey("visualizer_enabled")
        private val LIBRARY_SCAN_STAMP_KEY = stringPreferencesKey("library_scan_stamp")
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
            preferences[ACCENT_THEME_KEY] ?: "MINT"
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
    // Device-local cache marker: deliberately excluded from portable backups.
    val libraryScanStampFlow = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[LIBRARY_SCAN_STAMP_KEY].orEmpty() }
    suspend fun saveLibraryScanStamp(stamp: String) = dataStore.edit { it[LIBRARY_SCAN_STAMP_KEY] = stamp }
    val featureSettingsFlow = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { p ->
        FeatureSettings(p[RESUME_KEY] ?: true, (p[CROSSFADE_KEY] ?: 0).coerceIn(0, 12), p[REPLAY_GAIN_KEY] ?: "OFF",
            p[ONLY_FOLDERS_KEY] ?: false, p[INCLUDED_KEY].orEmpty(), p[EXCLUDED_KEY].orEmpty())
    }
    val playbackSnapshotFlow = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it[SNAPSHOT_KEY].orEmpty() }
    val equalizerFlow = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { it[EQ_KEY].orEmpty() }
    suspend fun updateFeatures(change: (FeatureSettings) -> FeatureSettings) = dataStore.edit { p ->
        val current = FeatureSettings(p[RESUME_KEY] ?: true, p[CROSSFADE_KEY] ?: 0, p[REPLAY_GAIN_KEY] ?: "OFF",
            p[ONLY_FOLDERS_KEY] ?: false, p[INCLUDED_KEY].orEmpty(), p[EXCLUDED_KEY].orEmpty())
        val settings = change(current)
        p[RESUME_KEY] = settings.resumePlayback
        p[CROSSFADE_KEY] = settings.crossfadeSeconds.coerceIn(0, 12)
        p[REPLAY_GAIN_KEY] = settings.replayGain.takeIf { it in listOf("OFF", "TRACK", "ALBUM") } ?: "OFF"
        p[ONLY_FOLDERS_KEY] = settings.selectedFoldersOnly
        p[INCLUDED_KEY] = settings.includedFolders; p[EXCLUDED_KEY] = settings.excludedFolders
    }
    suspend fun saveFeatures(settings: FeatureSettings) = dataStore.edit { p ->
        p[RESUME_KEY] = settings.resumePlayback
        p[CROSSFADE_KEY] = settings.crossfadeSeconds.coerceIn(0, 12)
        p[REPLAY_GAIN_KEY] = settings.replayGain.takeIf { it in listOf("OFF", "TRACK", "ALBUM") } ?: "OFF"
        p[ONLY_FOLDERS_KEY] = settings.selectedFoldersOnly
        p[INCLUDED_KEY] = settings.includedFolders
        p[EXCLUDED_KEY] = settings.excludedFolders
    }
    suspend fun savePlaybackSnapshot(snapshot: String) = dataStore.edit { it[SNAPSHOT_KEY] = snapshot }
    suspend fun saveEqualizer(json: String) = dataStore.edit { it[EQ_KEY] = json }

    suspend fun exportSettings(): JSONObject {
        val p = dataStore.data.first()
        return JSONObject().apply {
            put("dark", p[DARK_THEME_KEY] ?: true); put("accent", p[ACCENT_THEME_KEY] ?: "MINT")
            put("black", p[PURE_BLACK_KEY] ?: false); put("short", p[FILTER_SHORT_AUDIO_KEY] ?: false)
            put("speed", p[PLAYBACK_SPEED_KEY] ?: 1f); put("visualizer", p[VISUALIZER_ENABLED_KEY] ?: true)
            put("resume", p[RESUME_KEY] ?: true); put("crossfade", p[CROSSFADE_KEY] ?: 0)
            put("gain", p[REPLAY_GAIN_KEY] ?: "OFF"); put("onlyFolders", p[ONLY_FOLDERS_KEY] ?: false)
            put("included", JSONArray((p[INCLUDED_KEY] ?: emptySet()).toList()))
            put("excluded", JSONArray((p[EXCLUDED_KEY] ?: emptySet()).toList()))
            put("equalizer", p[EQ_KEY].orEmpty())
        }
    }
    suspend fun importSettings(j: JSONObject) = dataStore.edit { p ->
        p[DARK_THEME_KEY] = j.optBoolean("dark", true)
        p[ACCENT_THEME_KEY] = j.optString("accent", "MINT").takeIf { it in listOf("MINT", "DYNAMIC", "PURPLE", "CYAN", "SUNSET", "EMERALD", "GOLD") } ?: "MINT"
        p[PURE_BLACK_KEY] = j.optBoolean("black", false); p[FILTER_SHORT_AUDIO_KEY] = j.optBoolean("short", false)
        p[PLAYBACK_SPEED_KEY] = j.optDouble("speed", 1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
        p[VISUALIZER_ENABLED_KEY] = j.optBoolean("visualizer", true); p[RESUME_KEY] = j.optBoolean("resume", true)
        p[CROSSFADE_KEY] = j.optInt("crossfade", 0).coerceIn(0, 12)
        p[REPLAY_GAIN_KEY] = j.optString("gain", "OFF").takeIf { it in listOf("OFF", "TRACK", "ALBUM") } ?: "OFF"
        p[ONLY_FOLDERS_KEY] = j.optBoolean("onlyFolders", false)
        fun strings(name: String): Set<String> = j.optJSONArray(name)?.let { a -> (0 until minOf(a.length(), 10000)).map { a.optString(it) }.filter { it.startsWith("/") && it.length <= 4096 }.toSet() }.orEmpty()
        p[INCLUDED_KEY] = strings("included"); p[EXCLUDED_KEY] = strings("excluded")
        p[EQ_KEY] = j.optString("equalizer", "").take(10000)
    }

}
