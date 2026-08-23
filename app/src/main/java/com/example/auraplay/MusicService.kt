package com.example.auraplay.service

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.auraplay.data.Song
import com.example.auraplay.EqualizerManager
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
    private var player: ExoPlayer? = null
    private var equalizerManager: EqualizerManager? = null
    private var currentAudioSessionId: Int = 0
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // Companion object to hold the state flow, making it accessible app-wide.
    companion object {
        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()
        
        @Volatile
        private var instance: MusicService? = null
        
        fun getInstance(): MusicService? = instance
        
        fun getEqualizerManager(): EqualizerManager? {
            return instance?.equalizerManager
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(this) // Add listener to update state
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        // Initialize equalizer with audio session ID if valid
        val sessionId = exoPlayer.audioSessionId
        if (sessionId > 0) {
            currentAudioSessionId = sessionId
            equalizerManager = EqualizerManager(sessionId)
        }

        // Coroutine to regularly update the playback position
        serviceScope.launch {
            while (true) {
                player?.let { p ->
                    if (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING || p.isPlaying) {
                        val isPlayingState = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)
                        val curPos = p.currentPosition.coerceAtLeast(0L)
                        val dur = p.duration.coerceAtLeast(0L)

                        if (_playerState.value.isPlaying != isPlayingState ||
                            _playerState.value.currentPosition != curPos ||
                            _playerState.value.totalDuration != dur
                        ) {
                            _playerState.value = _playerState.value.copy(
                                isPlaying = isPlayingState,
                                currentPosition = curPos,
                                totalDuration = dur
                            )
                        }
                    }
                }
                delay(500)
            }
        }
    }

    // Update the state flow whenever a player event occurs.
    override fun onEvents(player: Player, events: Player.Events) {
        super.onEvents(player, events)

        val currentMediaItem = player.currentMediaItem
        val songFromMedia = if (currentMediaItem != null) {
            val rawId = currentMediaItem.mediaId.toLongOrNull() ?: 0L
            val rawTitle = currentMediaItem.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Title"
            val rawArtist = currentMediaItem.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val rawArtwork = currentMediaItem.mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            val rawData = currentMediaItem.localConfiguration?.uri?.toString() ?: ""
            val duration = player.duration.coerceAtLeast(0L)

            Song(
                id = rawId,
                title = rawTitle,
                artist = rawArtist,
                albumArtUri = rawArtwork,
                duration = duration,
                data = rawData
            )
        } else null

        val isPlayingState = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)

        _playerState.value = _playerState.value.copy(
            isPlaying = isPlayingState,
            isShuffleOn = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            totalDuration = player.duration.coerceAtLeast(0L),
            currentPosition = player.currentPosition.coerceAtLeast(0L),
            currentSong = songFromMedia ?: _playerState.value.currentSong
        )
        
        // Update equalizer audio session if it changed to a new valid ID
        (player as? ExoPlayer)?.audioSessionId?.let { sessionId ->
            if (sessionId > 0 && sessionId != currentAudioSessionId) {
                currentAudioSessionId = sessionId
                equalizerManager?.release()
                equalizerManager = EqualizerManager(sessionId)
            }
        }
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
        serviceJob.cancel()
        equalizerManager?.release()
        equalizerManager = null
        
        mediaSession?.run {
            release()
        }
        mediaSession = null
        
        player?.release()
        player = null
        
        instance = null
        super.onDestroy()
    }
}

