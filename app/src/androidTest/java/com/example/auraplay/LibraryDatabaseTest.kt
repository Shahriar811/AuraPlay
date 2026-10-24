package com.example.auraplay

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auraplay.data.*
import com.example.auraplay.features.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun song() = Song(1, "Before", "Artist", albumArtUri = null, duration = 60000, data = "/Music/one.mp3")

    @Test fun metadataScanPreservesConcurrentFavoriteAndHistory() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.playlistDao()
            val stale = song()
            dao.insertSongs(listOf(stale))
            dao.toggleFavorite(1)
            dao.recordSongPlayed(1, 12345)
            dao.syncSongs(emptyList(), listOf(stale.copy(title = "After", dateAdded = 42)), emptyList())
            val result = dao.getSongById(1).first()!!
            assertEquals("After", result.title)
            assertTrue(result.isFavorite)
            assertEquals(1, result.playCount)
            assertEquals(12345L, result.lastPlayedTimestamp)
            assertEquals(42L, result.dateAdded)
        } finally { database.close() }
    }

    @Test fun rapidFavoriteTogglesUseLatestDatabaseValue() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.playlistDao()
            dao.insertSongs(listOf(song()))
            dao.toggleFavorite(1)
            dao.toggleFavorite(1)
            assertFalse(dao.getSongById(1).first()!!.isFavorite)
        } finally { database.close() }
    }

    @Test fun songEditsSurviveRescanAndPreserveFavorites() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.playlistDao()
            dao.insertSongs(listOf(song()))
            dao.toggleFavorite(1)
            database.featureDao().saveEdit(SongEdit(1, "My title", "My artist", "My album", "file:///custom.jpg"))
            dao.syncSongs(emptyList(), listOf(song().copy(title = "Original file title")), emptyList())
            val result = dao.getSongById(1).first()!!
            assertEquals("My title", result.title); assertEquals("My artist", result.artist)
            assertEquals("file:///custom.jpg", result.albumArtUri); assertTrue(result.isFavorite)
        } finally { database.close() }
    }

    @Test fun bookmarksDeduplicateAndCascadeWhileListeningHistorySurvivesDeletion() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            database.playlistDao().insertSongs(listOf(song()))
            val features = database.featureDao()
            features.addBookmark(AudioBookmark(songId = 1, positionMs = 2000, label = "First"))
            features.addBookmark(AudioBookmark(songId = 1, positionMs = 2000, label = "Duplicate"))
            assertEquals(1, features.bookmarkList().size)
            features.recordEvent(ListeningEvent("event", 1, "Title", "Artist", 1, 1000))
            features.recordEvent(ListeningEvent("event", 1, "Title", "Artist", 1, 500))
            assertEquals(1000L, features.eventList().single().durationMs)
            database.playlistDao().deleteSongsByIds(listOf(1))
            assertTrue(features.bookmarkList().isEmpty())
            assertEquals(1, features.eventList().size)
        } finally { database.close() }
    }

    @Test fun versionFiveUpgradeRetainsPlaylistAndFavorites() = runBlocking {
        val name = "migration-test-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE Song (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, albumId INTEGER NOT NULL, albumArtUri TEXT, duration INTEGER NOT NULL, data TEXT NOT NULL, folderPath TEXT NOT NULL, playCount INTEGER NOT NULL, lastPlayedTimestamp INTEGER NOT NULL, isFavorite INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE Playlist (playlistId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE PlaylistSongCrossRef (playlistId INTEGER NOT NULL, id INTEGER NOT NULL, PRIMARY KEY(playlistId, id), FOREIGN KEY(playlistId) REFERENCES Playlist(playlistId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(id) REFERENCES Song(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_PlaylistSongCrossRef_playlistId ON PlaylistSongCrossRef(playlistId)")
            db.execSQL("CREATE INDEX index_PlaylistSongCrossRef_id ON PlaylistSongCrossRef(id)")
            db.execSQL("CREATE TABLE SongLyricsEntity (songId INTEGER NOT NULL PRIMARY KEY, isSynced INTEGER NOT NULL, syncedLyrics TEXT NOT NULL, plainLyrics TEXT NOT NULL, source TEXT NOT NULL)")
            db.execSQL("INSERT INTO Song VALUES(1, 'Saved song', 'Artist', 'Album', 1, NULL, 60000, '/Music/one.mp3', '/Music', 7, 100, 1)")
            db.execSQL("INSERT INTO Playlist VALUES(1, 'Keep me')")
            db.execSQL("INSERT INTO PlaylistSongCrossRef VALUES(1, 1)")
            db.version = 5
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val playlist = database.playlistDao().getPlaylistWithSongs(1).first()!!
            assertEquals("Keep me", playlist.playlist.name)
            assertTrue(playlist.songs.single().isFavorite)
            assertEquals(7, playlist.songs.single().playCount)
            assertEquals(0L, playlist.songs.single().dateAdded)
            database.featureDao().addBookmark(AudioBookmark(songId = 1, positionMs = 1234, label = "After upgrade"))
            assertEquals("After upgrade", database.featureDao().bookmarkList().single().label)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
