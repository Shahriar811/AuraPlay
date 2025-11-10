package com.example.auraplay.service

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.auraplay.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Data class to hold the complete, current player state.
// THIS IS THE DEFINITION THAT WAS MISSING.
data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)

class MusicService : MediaSessionService(), Player.Listener {
    private var mediaSession: MediaSession? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // Companion object to hold the state flow, making it accessible app-wide.
    companion object {
        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        player.addListener(this) // Add listener to update state
        mediaSession = MediaSession.Builder(this, player).build()

        // Coroutine to regularly update the playback position
        serviceScope.launch {
            while (true) {
                _playerState.value = _playerState.value.copy(
                    currentPosition = player.currentPosition.coerceAtLeast(0)
                )
                delay(1000)
            }
        }
    }

    // Update the state flow whenever a player event occurs.
    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)

        // This logic will require access to the song list, which the service doesn't have.
        // We'll update the song information from MainActivity.
        val currentMediaItem = player.currentMediaItem
        // A placeholder for the song. The actual song object will be updated from MainActivity.
        val songFromMedia = if (currentMediaItem != null) {
            Song(
                id = currentMediaItem.mediaId.toLong(),
                title = currentMediaItem.mediaMetadata.title.toString(),
                artist = currentMediaItem.mediaMetadata.artist.toString(),
                albumArtUri = currentMediaItem.mediaMetadata.artworkUri.toString(),
                duration = player.duration,
                data = currentMediaItem.localConfiguration?.uri.toString()
            )
        } else null

        _playerState.value = _playerState.value.copy(
            isPlaying = player.isPlaying,
            isShuffleOn = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            totalDuration = player.duration.coerceAtLeast(0),
            currentSong = songFromMedia ?: _playerState.value.currentSong
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let {
            if (!it.playWhenReady || it.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
    }
}

