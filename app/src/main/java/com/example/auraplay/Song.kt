package com.example.auraplay.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Song(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String?, // Store URI as string
    val duration: Long,
    val data: String, // Path to the audio file
    val isFavorite: Boolean = false // New field for favorites
)