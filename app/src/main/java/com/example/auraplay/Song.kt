package com.example.auraplay.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Song(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String = "Unknown Album",
    val albumId: Long = -1L,
    val albumArtUri: String?, // Store URI as string
    val duration: Long,
    val data: String, // Path to the audio file
    val folderPath: String = "",
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val isFavorite: Boolean = false // Field for favorites
)