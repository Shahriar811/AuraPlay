package com.example.auraplay.features

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun Song.toMediaItem(queueKey: String = UUID.randomUUID().toString()): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(if (data.startsWith("content:") || data.startsWith("file:")) Uri.parse(data)
        else ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
        .setArtworkUri(albumArtUri?.let(Uri::parse))
        .setExtras(Bundle().apply {
            putString("queue_key", queueKey); putString("source_path", data); putLong("song_duration", duration)
            putLong("album_id", albumId); putLong("date_added", dateAdded)
        }).build()).build()

data class PlaybackSnapshot(val ids: List<Long>, val index: Int, val position: Long, val shuffle: Boolean, val repeat: Int,
    val order: List<Int> = emptyList()) {
    fun encode(): String = JSONObject().put("ids", JSONArray(ids)).put("index", index)
        .put("position", position.coerceAtLeast(0)).put("shuffle", shuffle).put("repeat", repeat).put("order", JSONArray(order)).toString()

    companion object {
        fun decode(json: String): PlaybackSnapshot? = try {
            val j = JSONObject(json)
            val ids = j.getJSONArray("ids")
            require(ids.length() <= 50_000)
            val tracks = (0 until ids.length()).map { ids.getLong(it) }
            val sequence = j.optJSONArray("order")?.let { a ->
                if (a.length() == tracks.size) (0 until a.length()).map { a.getInt(it) }.takeIf { it.toSet() == tracks.indices.toSet() } else null
            }.orEmpty()
            if (tracks.isEmpty()) null else PlaybackSnapshot(tracks, j.optInt("index").coerceIn(tracks.indices),
                j.optLong("position").coerceAtLeast(0), j.optBoolean("shuffle"), j.optInt("repeat").coerceIn(0, 2), sequence)
        } catch (_: Exception) { null }
    }
}

data class RestoredPlayback(val songs: List<Song>, val index: Int, val position: Long, val order: List<Int> = emptyList())

/** Build library-sized queue metadata off the main thread, before publishing a resume preview. */
internal fun resumePreviewState(queue: RestoredPlayback?, snapshot: PlaybackSnapshot?): PlayerState =
    if (queue == null) PlayerState() else PlayerState(
        currentSong = queue.songs[queue.index], currentPosition = queue.position,
        totalDuration = queue.songs[queue.index].duration, isShuffleOn = snapshot?.shuffle ?: false,
        repeatMode = snapshot?.repeat ?: 0, currentQueueIndex = queue.index, queueSize = queue.songs.size,
        queue = queue.songs, queueKeys = queue.songs.mapIndexed { i, song -> "resume-$i-${song.id}" },
        queueOrder = if (snapshot?.shuffle == true) queue.order else queue.songs.indices.toList())

fun restoreQueue(ids: List<Long>, index: Int, position: Long, library: List<Song>, order: List<Int> = emptyList()): RestoredPlayback? {
    val byId = library.associateBy { it.id }
    val available = ids.mapIndexedNotNull { i, id -> byId[id]?.let { i to it } }
    if (available.isEmpty()) return null
    val selected = available.indexOfFirst { it.first >= index }.takeIf { it >= 0 } ?: available.lastIndex
    val sameTrack = available[selected].first == index
    val duration = available[selected].second.duration
    val validOrder = order.takeIf { it.size == ids.size && it.toSet() == ids.indices.toSet() } ?: ids.indices.toList()
    val positions = available.mapIndexed { newIndex, entry -> entry.first to newIndex }.toMap()
    return RestoredPlayback(available.map { it.second }, selected,
        if (!sameTrack) 0 else position.coerceIn(0, duration.coerceAtLeast(0)), validOrder.mapNotNull(positions::get))
}
