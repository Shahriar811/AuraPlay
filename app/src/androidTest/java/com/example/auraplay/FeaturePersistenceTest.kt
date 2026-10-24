package com.example.auraplay

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auraplay.data.*
import com.example.auraplay.features.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FeaturePersistenceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuraPlayApplication

    @Test fun backupRoundTripMergesAndRemapsTrackIdsWithoutDuplicates() = runBlocking {
        val settings = app.settingsDataStore
        val original = settings.exportSettings()
        val oldStyle = com.example.auraplay.widget.WidgetStyles.default(app)
        val source = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val file = File(app.cacheDir, "roundtrip-${System.nanoTime()}.zip")
        try {
            val track = Song(10, "Custom title", "Artist", duration = 60000, albumArtUri = null, data = "/Music/song.mp3", isFavorite = true, playCount = 7, lastPlayedTimestamp = 1000)
            source.playlistDao().insertSongs(listOf(track))
            val listId = source.playlistDao().insertPlaylist(Playlist(name = "My playlist"))
            source.playlistDao().addSongToPlaylist(PlaylistSongCrossRef(listId, 10))
            source.playlistDao().insertPlaylist(Playlist(name = "My playlist"))
            source.featureDao().saveEdit(SongEdit(10, "Custom title", "Artist", "Album", null))
            source.featureDao().addBookmark(AudioBookmark(songId = 10, positionMs = 12345, label = "Chorus", createdAt = 2000))
            source.featureDao().recordEvent(ListeningEvent("portable-event", 10, "Title", "Artist", 1000, 20000))
            settings.updateFeatures { it.copy(crossfadeSeconds = 4, replayGain = "TRACK") }
            BackupManager(app, settings, source).export(Uri.fromFile(file))
            target.playlistDao().insertSongs(listOf(track.copy(id = 90, title = "Original title", isFavorite = false, playCount = 1, lastPlayedTimestamp = 0)))
            BackupManager(app, settings, target).restore(Uri.fromFile(file))
            BackupManager(app, settings, target).restore(Uri.fromFile(file))
            val restored = target.playlistDao().getSongById(90).first()!!
            assertTrue(restored.isFavorite); assertEquals(7, restored.playCount); assertEquals("Custom title", restored.title)
            assertEquals(2, target.playlistDao().playlistList().size)
            assertEquals(90L, target.playlistDao().playlistList().flatMap { target.playlistDao().playlistSongList(it.playlistId) }.single().id)
            assertEquals(90L, target.featureDao().bookmarkList().single().songId)
            assertEquals(12345L, target.featureDao().bookmarkList().single().positionMs)
            assertEquals(1, target.featureDao().eventList().size)
            assertEquals(4, settings.featureSettingsFlow.first().crossfadeSeconds)
        } finally {
            settings.importSettings(original); com.example.auraplay.widget.WidgetStyles.setDefault(app, oldStyle)
            source.close(); target.close(); file.delete()
        }
    }

    @Test fun invalidBackupCannotModifyLibrary() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val file = File(app.cacheDir, "invalid-${System.nanoTime()}.zip")
        try {
            ZipOutputStream(file.outputStream()).use { zip -> zip.putNextEntry(ZipEntry("../invalid")); zip.write(byteArrayOf(1)); zip.closeEntry() }
            var rejected = false
            try { BackupManager(app, app.settingsDataStore, database).restore(Uri.fromFile(file)) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected); assertTrue(database.playlistDao().playlistList().isEmpty())
        } finally { database.close(); file.delete() }
    }

    @Test fun concurrentSettingChangesKeepBothUpdates() = runBlocking {
        val settings = app.settingsDataStore
        val original = settings.exportSettings()
        try {
            coroutineScope {
                launch(Dispatchers.IO) { settings.updateFeatures { it.copy(crossfadeSeconds = 8) } }
                launch(Dispatchers.IO) { settings.updateFeatures { it.copy(replayGain = "ALBUM") } }
                launch(Dispatchers.IO) { settings.updateFeatures { it.copy(includedFolders = setOf("/Music/A")) } }
            }
            val result = settings.featureSettingsFlow.first()
            assertEquals(8, result.crossfadeSeconds); assertEquals("ALBUM", result.replayGain); assertEquals(setOf("/Music/A"), result.includedFolders)
        } finally { settings.importSettings(original) }
    }
}
