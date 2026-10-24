package com.example.auraplay.data

import com.example.auraplay.features.*
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class, SongLyricsEntity::class, SongEdit::class, AudioBookmark::class, ListeningEvent::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun featureDao(): FeatureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Adds the isFavorite column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Song ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Older releases omitted migrations. Inspect the existing columns to preserve libraries,
        // favorites and playlists when upgrading any of those versions.
        private fun migrateLibrary(from: Int) = object : Migration(from, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = mutableSetOf<String>()
                db.query("PRAGMA table_info(`Song`)").use { cursor ->
                    val name = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) columns.add(cursor.getString(name))
                }
                val additions = mapOf(
                    "album" to "TEXT NOT NULL DEFAULT 'Unknown Album'",
                    "albumId" to "INTEGER NOT NULL DEFAULT -1",
                    "folderPath" to "TEXT NOT NULL DEFAULT ''",
                    "playCount" to "INTEGER NOT NULL DEFAULT 0",
                    "lastPlayedTimestamp" to "INTEGER NOT NULL DEFAULT 0",
                    "isFavorite" to "INTEGER NOT NULL DEFAULT 0",
                    "dateAdded" to "INTEGER NOT NULL DEFAULT 0"
                )
                additions.forEach { (name, definition) ->
                    if (name !in columns) db.execSQL("ALTER TABLE `Song` ADD COLUMN `$name` $definition")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS `SongLyricsEntity` (`songId` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, `syncedLyrics` TEXT NOT NULL, `plainLyrics` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`songId`))")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS SongEdit (songId INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, artwork TEXT, FOREIGN KEY(songId) REFERENCES Song(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS AudioBookmark (bookmarkId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, songId INTEGER NOT NULL, positionMs INTEGER NOT NULL, label TEXT NOT NULL, createdAt INTEGER NOT NULL, FOREIGN KEY(songId) REFERENCES Song(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_AudioBookmark_songId_positionMs ON AudioBookmark(songId, positionMs)")
                db.execSQL("CREATE TABLE IF NOT EXISTS ListeningEvent (eventId TEXT NOT NULL PRIMARY KEY, songId INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ListeningEvent_startedAt ON ListeningEvent(startedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ListeningEvent_songId ON ListeningEvent(songId)")
            }
        }
        val MIGRATIONS = arrayOf(migrateLibrary(1), migrateLibrary(2), migrateLibrary(3), migrateLibrary(4), migrateLibrary(5), MIGRATION_6_7)

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auraplay_database"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
