package com.example.auraplay.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SongLyricsEntity(
    @PrimaryKey val songId: Long,
    val isSynced: Boolean,
    val syncedLyrics: String,
    val plainLyrics: String,
    val source: String = "LRCLIB" // "LOCAL" or "LRCLIB"
)
