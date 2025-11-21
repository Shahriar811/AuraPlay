package com.example.auraplay.data

import androidx.room.Entity

@Entity(primaryKeys = ["playlistId", "id"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val id: Long // Song ID
)
