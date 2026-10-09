package com.example.auraplay.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.auraplay.AuraPlayApplication
import com.example.auraplay.MainActivity
import com.example.auraplay.data.Song
import com.example.auraplay.EqualizerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val isEndOfTrack: Boolean = false
)

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val totalDuration: Long = 0,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val currentQueueIndex: Int = 0,
    val queueSize: Int = 0,
    val queue: List<Song> = emptyList()
)

class MusicService : MediaSessionService(), Player.Listener {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var equalizerManager: EqualizerManager? = null
    private var currentAudioSessionId: Int = 0
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var sleepTimerJob: Job? = null
    private var lastRecordedSongId: Long = -1L

    // Audio focus & external audio interruption management
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedByExternalAudio: Boolean = false
    private var isHandlingAudioFocusLoss: Boolean = false
    private var resumeCheckJob: Job? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                resumeCheckJob?.cancel()
                if (pausedByExternalAudio) {
                    pausedByExternalAudio = false
                    player?.volume = 1.0f
                    player?.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            AudioManager.AUDIOFOCUS_LOSS -> {
                val wasPlaying = player?.isPlaying == true || player?.playWhenReady == true
                if (wasPlaying) {
                    pausedByExternalAudio = true
                    isHandlingAudioFocusLoss = true
                    player?.pause()
                    isHandlingAudioFocusLoss = false
                }
            }
        }
    }

    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            super.onPlaybackConfigChanged(configs)
            checkAndResumeIfOtherAudioStopped()
        }
    }

    private fun checkAndResumeIfOtherAudioStopped() {
        if (!pausedByExternalAudio) return
        resumeCheckJob?.cancel()
        resumeCheckJob = serviceScope.launch {
            // Debounce slightly to allow the OS and external app audio state to stabilize
            delay(350)
            if (pausedByExternalAudio && !audioManager.isMusicActive()) {
                val focusResult = requestAudioFocusInternal()
                if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    pausedByExternalAudio = false
                    player?.volume = 1.0f
                    player?.play()
                }
            }
        }
    }

    private fun requestAudioFocusInternal(): Int {
        val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AndroidAudioAttributes.Builder()
                    .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                    .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
            .build().also { audioFocusRequest = it }

        return audioManager.requestAudioFocus(req)
    }

    private fun abandonAudioFocusInternal() {
        audioFocusRequest?.let { req ->
            try {
                audioManager.abandonAudioFocusRequest(req)
            } catch (e: Exception) {
                // Safety
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausedByExternalAudio = false
                resumeCheckJob?.cancel()
                player?.pause()
            }
        }
    }

    companion object {
        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()

        private val _sleepTimerState = MutableStateFlow(SleepTimerState())
        val sleepTimerState = _sleepTimerState.asStateFlow()
        
        @Volatile
        private var instance: MusicService? = null
        
        fun getInstance(): MusicService? = instance
        
        fun getEqualizerManager(): EqualizerManager? {
            return instance?.equalizerManager
        }

        fun startSleepTimer(minutes: Int) {
            instance?.startTimer(minutes * 60L, isEndOfTrack = false)
        }

        fun startSleepTimerEndOfTrack() {
            instance?.startTimer(0L, isEndOfTrack = true)
        }

        fun cancelSleepTimer() {
            instance?.stopTimer()
        }

        fun setPlaybackSpeed(speed: Float) {
            instance?.applySpeed(speed)
        }

        fun togglePlayPause() {
            instance?.player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
        }

        fun seekToNext() {
            instance?.player?.seekToNext()
        }

        fun seekToPrevious() {
            instance?.player?.seekToPrevious()
        }

        fun playQueueItem(index: Int) {
            instance?.player?.let { p ->
                if (index in 0 until p.mediaItemCount) {
                    p.seekTo(index, 0)
                    p.play()
                }
            }
        }

        fun removeQueueItem(index: Int) {
            instance?.player?.let { p ->
                if (index in 0 until p.mediaItemCount) {
                    p.removeMediaItem(index)
                    instance?.updateQueueState()
                }
            }
        }

        fun moveQueueItem(fromIndex: Int, toIndex: Int) {
            instance?.player?.let { p ->
                if (fromIndex in 0 until p.mediaItemCount && toIndex in 0 until p.mediaItemCount) {
                    p.moveMediaItem(fromIndex, toIndex)
                    instance?.updateQueueState()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            audioManager.registerAudioPlaybackCallback(audioPlaybackCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            // Safety fallback
        }

        val exoPlayer = ExoPlayer.Builder(this).build()
        val media3AudioAttributes = Media3AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        exoPlayer.setAudioAttributes(media3AudioAttributes, false)
        exoPlayer.addListener(this)
        player = exoPlayer

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()

        val sessionId = exoPlayer.audioSessionId
        if (sessionId > 0) {
            currentAudioSessionId = sessionId
            equalizerManager = EqualizerManager(sessionId)
        }

        // Register noisy receiver
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, filter)

        // Coroutine to regularly update playback position and handle sleep timer end-of-track
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

                        // Record play count if song played past 10 seconds
                        _playerState.value.currentSong?.id?.let { songId ->
                            if (songId > 0 && curPos >= 10_000L && lastRecordedSongId != songId) {
                                lastRecordedSongId = songId
                                (application as? AuraPlayApplication)?.let { app ->
                                    serviceScope.launch(Dispatchers.IO) {
                                        app.database.playlistDao().recordSongPlayed(songId, System.currentTimeMillis())
                                    }
                                }
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun applySpeed(speed: Float) {
        player?.let { p ->
            p.playbackParameters = PlaybackParameters(speed)
            _playerState.value = _playerState.value.copy(playbackSpeed = speed)
        }
    }

    private fun startTimer(totalSeconds: Long, isEndOfTrack: Boolean) {
        sleepTimerJob?.cancel()
        if (isEndOfTrack) {
            _sleepTimerState.value = SleepTimerState(isActive = true, remainingSeconds = 0, isEndOfTrack = true)
        } else {
            _sleepTimerState.value = SleepTimerState(isActive = true, remainingSeconds = totalSeconds, isEndOfTrack = false)
            sleepTimerJob = serviceScope.launch {
                var remaining = totalSeconds
                while (remaining > 0) {
                    delay(1000)
                    remaining--
                    _sleepTimerState.value = _sleepTimerState.value.copy(remainingSeconds = remaining)

                    // Smooth fade out in the last 20 seconds
                    if (remaining in 1..20) {
                        val volume = (remaining.toFloat() / 20f).coerceIn(0.05f, 1.0f)
                        player?.volume = volume
                    }
                }

                // Timer expired -> pause and reset volume
                pausedByExternalAudio = false
                resumeCheckJob?.cancel()
                player?.pause()
                player?.volume = 1.0f
                _sleepTimerState.value = SleepTimerState(isActive = false, remainingSeconds = 0)
            }
        }
    }

    private fun stopTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        player?.volume = 1.0f
        _sleepTimerState.value = SleepTimerState(isActive = false, remainingSeconds = 0)
    }

    private fun updateQueueState() {
        player?.let { p ->
            val queueItems = mutableListOf<Song>()
            for (i in 0 until p.mediaItemCount) {
                val item = p.getMediaItemAt(i)
                val id = item.mediaId.toLongOrNull() ?: 0L
                val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Title"
                val artist = item.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                val artwork = item.mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                val uri = item.localConfiguration?.uri?.toString() ?: ""
                queueItems.add(Song(id = id, title = title, artist = artist, albumArtUri = artwork, duration = 0L, data = uri))
            }
            _playerState.value = _playerState.value.copy(
                queue = queueItems,
                queueSize = queueItems.size,
                currentQueueIndex = p.currentMediaItemIndex
            )
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        super.onPlayWhenReadyChanged(playWhenReady, reason)
        if (playWhenReady) {
            if (!isHandlingAudioFocusLoss) {
                pausedByExternalAudio = false
                resumeCheckJob?.cancel()
                val focusResult = requestAudioFocusInternal()
                if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED &&
                    focusResult != AudioManager.AUDIOFOCUS_REQUEST_DELAYED
                ) {
                    isHandlingAudioFocusLoss = true
                    player?.pause()
                    isHandlingAudioFocusLoss = false
                }
            }
        } else {
            if (!isHandlingAudioFocusLoss) {
                // User or manual interaction intentionally paused playback
                pausedByExternalAudio = false
                resumeCheckJob?.cancel()
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        // If sleep timer was set for end of track, stop now
        if (_sleepTimerState.value.isActive && _sleepTimerState.value.isEndOfTrack) {
            pausedByExternalAudio = false
            resumeCheckJob?.cancel()
            player?.pause()
            _sleepTimerState.value = SleepTimerState(isActive = false, remainingSeconds = 0)
        }
        updateQueueState()
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        super.onTimelineChanged(timeline, reason)
        updateQueueState()
    }

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
            playbackSpeed = player.playbackParameters.speed,
            currentQueueIndex = player.currentMediaItemIndex,
            currentSong = songFromMedia ?: _playerState.value.currentSong
        )
        
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
        resumeCheckJob?.cancel()
        try {
            audioManager.unregisterAudioPlaybackCallback(audioPlaybackCallback)
        } catch (e: Exception) {
            // Unregister safety
        }
        abandonAudioFocusInternal()

        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (e: Exception) {
            // Unregister safety
        }
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


