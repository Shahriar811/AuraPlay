package com.example.auraplay.features

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FeatureDao {
    @Query("SELECT * FROM SongEdit") suspend fun edits(): List<SongEdit>
    @Upsert suspend fun saveEdit(edit: SongEdit)
    @Query("DELETE FROM SongEdit WHERE songId = :id") suspend fun deleteEdit(id: Long)
    @Query("SELECT * FROM AudioBookmark ORDER BY createdAt DESC") fun bookmarks(): Flow<List<AudioBookmark>>
    @Query("SELECT * FROM AudioBookmark WHERE songId = :id ORDER BY positionMs") fun bookmarksForSong(id: Long): Flow<List<AudioBookmark>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addBookmark(bookmark: AudioBookmark): Long
    @Delete suspend fun deleteBookmark(bookmark: AudioBookmark)
    @Query("SELECT * FROM AudioBookmark") suspend fun bookmarkList(): List<AudioBookmark>
    @Query("SELECT * FROM ListeningEvent ORDER BY startedAt DESC") fun listeningEvents(): Flow<List<ListeningEvent>>
    @Query("SELECT * FROM ListeningEvent") suspend fun eventList(): List<ListeningEvent>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvent(event: ListeningEvent)
    @Query("UPDATE ListeningEvent SET durationMs = MAX(durationMs, :duration) WHERE eventId = :id") suspend fun updateDuration(id: String, duration: Long)
    @Transaction suspend fun recordEvent(event: ListeningEvent) { insertEvent(event); updateDuration(event.eventId, event.durationMs) }
}
