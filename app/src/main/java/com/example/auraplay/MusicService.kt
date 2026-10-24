package com.example.auraplay.service

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.auraplay.AuraPlayApplication
import com.example.auraplay.MainActivity
import com.example.auraplay.EqualizerManager
import com.example.auraplay.data.Song
import com.example.auraplay.features.*
import com.example.auraplay.widget.AuraPlayAppWidgetProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

 data class SleepTimerState(val isActive: Boolean = false, val remainingSeconds: Long = 0L, val isEndOfTrack: Boolean = false)
 data class PlayerState(
    val currentSong: Song? = null, val isPlaying: Boolean = false, val currentPosition: Long = 0,
    val totalDuration: Long = 0, val isShuffleOn: Boolean = false, val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1f, val currentQueueIndex: Int = 0, val queueSize: Int = 0,
    val queue: List<Song> = emptyList(), val queueKeys: List<String> = emptyList(), val queueOrder: List<Int> = emptyList()
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicService : MediaSessionService(), Player.Listener {
    private val app get() = application as AuraPlayApplication
    private var mediaSession: MediaSession? = null
    private var audio: AudioPlayer? = null
    private var playbackRecovery: PlaybackRecovery? = null
    private val player: ExoPlayer? get() = audio?.player
    private var equalizerManager: EqualizerManager? = null
    private var oldEqualizer: EqualizerManager? = null
    private var eqObserver: Job? = null
    private var savedEqualizer = ""
    private var audioSessionId = 0
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var crossfade: CrossfadeEngine
    private var restoreJob: Deferred<Unit>? = null
    private var restored = false
    private var features = FeatureSettings()
    private var sleepTimerJob: Job? = null
    private var sleepVolume = 1f
    private var trackedSong: Song? = null
    private var eventId = UUID.randomUUID().toString()
    private var eventStarted = System.currentTimeMillis()
    private var listenedMs = 0L
    private var eventListenedMs = 0L
    private var recordedPlay = false
    private var lastSample = SystemClock.elapsedRealtime()
    private var wasPlaying = false
    private var lastPersist = 0L

    companion object {
        private val _playerState = MutableStateFlow(PlayerState())
        val playerState = _playerState.asStateFlow()
        private val _sleepTimerState = MutableStateFlow(SleepTimerState())
        val sleepTimerState = _sleepTimerState.asStateFlow()
        private val _equalizerManagerState = MutableStateFlow<EqualizerManager?>(null)
        val equalizerManagerState = _equalizerManagerState.asStateFlow()
        @Volatile private var instance: MusicService? = null
        fun getInstance(): MusicService? = instance
        fun getEqualizerManager(): EqualizerManager? = instance?.equalizerManager
        suspend fun awaitRestoration() { instance?.restoreJob?.await() }
        fun publishResumePreview(preview: PlayerState) {
            if (instance != null) return
            _playerState.value = preview
        }
        fun startSleepTimer(minutes: Int) { instance?.startTimer(minutes * 60L, false) }
        fun startSleepTimerEndOfTrack() { instance?.startTimer(0L, true) }
        fun cancelSleepTimer() { instance?.stopTimer() }
        fun setPlaybackSpeed(speed: Float) { instance?.player?.setPlaybackSpeed(speed.coerceIn(0.5f, 2f)) }
        fun togglePlayPause() { instance?.player?.let { if (it.isPlaying) it.pause() else it.playOrRetry() } }
        fun seekToNext() { instance?.crossfade?.cancel(); instance?.player?.seekToNextOrRetry() }
        fun seekToPrevious() { instance?.crossfade?.cancel(); instance?.player?.seekToPreviousOrRetry() }
        fun playQueueItem(index: Int) { instance?.let { s -> s.crossfade.cancel(); s.player?.let { p -> AudioEngine.order(p).getOrNull(index)?.let { p.seekTo(it, 0); p.playOrRetry() } } } }
        fun removeQueueItem(index: Int) { instance?.let { s -> s.crossfade.cancel(); s.player?.let { p -> AudioEngine.order(p).getOrNull(index)?.let(p::removeMediaItem) } } }
        fun moveQueueItem(fromIndex: Int, toIndex: Int) { instance?.let { s -> s.crossfade.cancel(); s.player?.let { p ->
            val order = AudioEngine.order(p).toMutableList()
            if (fromIndex in order.indices && toIndex in order.indices) {
                if (p.shuffleModeEnabled) { order.add(toIndex, order.removeAt(fromIndex)); p.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime())) }
                else p.moveMediaItem(fromIndex, toIndex)
                s.updateQueue(); s.persistPlayback()
            }
        } } }
        fun enqueue(song: Song, next: Boolean) { instance?.addToQueue(song, next) }
        fun applyEqualizer(json: String) { instance?.let { it.savedEqualizer = json; it.equalizerManager?.restore(decodeEqualizer(json)) } }
        fun refreshMetadata(song: Song) { instance?.let { s ->
            s.crossfade.cancel()
            s.player?.let { p -> for (i in 0 until p.mediaItemCount) if (p.getMediaItemAt(i).mediaId == song.id.toString()) {
                p.replaceMediaItem(i, song.toMediaItem(p.getMediaItemAt(i).mediaMetadata.extras?.getString("queue_key") ?: UUID.randomUUID().toString()))
            } }
        } }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audio = AudioEngine.create(this, true) { features.replayGain }
        player!!.addListener(this)
        attachPlaybackRecovery(player!!)
        crossfade = CrossfadeEngine(this, serviceScope, { audio }, { features }, {
            val timer = _sleepTimerState.value
            !timer.isEndOfTrack && (!timer.isActive || timer.remainingSeconds > features.crossfadeSeconds + 20)
        }, { sleepVolume }, { incoming -> switchPlayer(incoming) }, { oldEqualizer?.release(); oldEqualizer = null })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mediaSession = MediaSession.Builder(this, player!!).setSessionActivity(open).build()
        restoreJob = serviceScope.async {
            try {
                val config = app.settingsDataStore.featureSettingsFlow.first()
                features = config
                savedEqualizer = app.settingsDataStore.equalizerFlow.first()
                if (config.resumePlayback) {
                    val json = app.settingsDataStore.playbackSnapshotFlow.first()
                    val snapshot = withContext(Dispatchers.Default) { PlaybackSnapshot.decode(json) }
                    val queue = snapshot?.let { saved -> withContext(Dispatchers.IO) { restoreQueue(saved.ids, saved.index, saved.position, app.database.playlistDao().getAllSongsList(), saved.order) } }
                    if (queue != null && player?.mediaItemCount == 0) {
                        val items = withContext(Dispatchers.Default) { queue.songs.map { it.toMediaItem() } }
                        player?.setMediaItems(items, queue.index, queue.position)
                        player?.setShuffleOrder(DefaultShuffleOrder(queue.order.toIntArray(), System.nanoTime()))
                        player?.shuffleModeEnabled = snapshot.shuffle
                        player?.repeatMode = snapshot.repeat
                        player?.prepare()
                        player?.playWhenReady = false
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("MusicService", "Playback restoration unavailable", error) }
            finally { restored = true; updateQueue(); publishState() }
        }
        serviceScope.launch { app.settingsDataStore.featureSettingsFlow.distinctUntilChanged().collect {
            if (features.crossfadeSeconds != it.crossfadeSeconds) crossfade.cancel()
            features = it
            audio?.let { bundle -> AudioEngine.refreshGain(bundle, it.replayGain) }
            if (restored) persistPlayback()
        } }
        serviceScope.launch { app.settingsDataStore.playbackSpeedFlow.distinctUntilChanged().collect { player?.setPlaybackSpeed(it.coerceIn(0.5f, 2f)) } }
        serviceScope.launch {
            while (isActive) {
                sampleListening()
                publishState()
                crossfade.tick()
                if (SystemClock.elapsedRealtime() - lastPersist >= 5000) {
                    lastPersist = SystemClock.elapsedRealtime(); flushListening(); persistPlayback()
                }
                delay(250)
            }
        }
    }

    private fun switchPlayer(incoming: AudioPlayer) {
        sampleListening(); flushListening()
        playbackRecovery?.let { recovery -> recovery.release(); player?.removeListener(recovery) }
        player?.removeListener(this)
        oldEqualizer?.release(); oldEqualizer = equalizerManager
        eqObserver?.cancel(); equalizerManager = null; audioSessionId = 0
        audio = incoming
        AudioEngine.refreshGain(incoming, features.replayGain)
        incoming.player.addListener(this)
        attachPlaybackRecovery(incoming.player)
        mediaSession?.setPlayer(incoming.player)
        beginListening(songFrom(incoming.player.currentMediaItem))
        updateQueue(); publishState(); ensureEqualizer()
        AuraPlayAppWidgetProvider.updateAllWidgets(this)
    }

    private fun addToQueue(song: Song, next: Boolean) {
        crossfade.cancel()
        val p = player ?: return
        if (p.mediaItemCount == 0) { p.setMediaItem(song.toMediaItem()); p.prepare(); p.play(); return }
        val index = if (next) (p.currentMediaItemIndex + 1).coerceIn(0, p.mediaItemCount) else p.mediaItemCount
        val order = if (next && p.shuffleModeEnabled) AudioEngine.order(p) else emptyList()
        val currentIndex = p.currentMediaItemIndex
        p.addMediaItem(index, song.toMediaItem())
        if (order.isNotEmpty()) {
            val shifted = order.map { if (it >= index) it + 1 else it }.toMutableList()
            val currentShifted = if (currentIndex >= index) currentIndex + 1 else currentIndex
            shifted.add((shifted.indexOf(currentShifted) + 1).coerceIn(0, shifted.size), index)
            p.setShuffleOrder(DefaultShuffleOrder(shifted.toIntArray(), System.nanoTime()))
        }
        updateQueue(); persistPlayback()
    }

    private fun songFrom(item: MediaItem?): Song? = item?.let {
        val metadata = it.mediaMetadata
        val extra = metadata.extras
        Song(it.mediaId.toLongOrNull() ?: 0, metadata.title?.toString() ?: "Unknown Title", metadata.artist?.toString() ?: "Unknown Artist",
            metadata.albumTitle?.toString() ?: "Unknown Album", extra?.getLong("album_id", -1) ?: -1,
            metadata.artworkUri?.toString(), extra?.getLong("song_duration", 0) ?: 0,
            extra?.getString("source_path") ?: it.localConfiguration?.uri?.toString().orEmpty(), dateAdded = extra?.getLong("date_added", 0) ?: 0)
    }

    private fun updateQueue() {
        val p = player ?: return
        val items = (0 until p.mediaItemCount).map(p::getMediaItemAt)
        _playerState.value = _playerState.value.copy(queue = items.mapNotNull(::songFrom), queueSize = items.size,
            currentQueueIndex = p.currentMediaItemIndex,
            queueKeys = items.mapIndexed { i, item -> item.mediaMetadata.extras?.getString("queue_key") ?: "item-$i-${item.mediaId}" }, queueOrder = AudioEngine.order(p))
    }

    private fun publishState() {
        val p = player ?: return
        val song = songFrom(p.currentMediaItem)
        val duration = p.duration.takeIf { it > 0 } ?: song?.duration ?: 0
        _playerState.value = _playerState.value.copy(currentSong = song, currentQueueIndex = p.currentMediaItemIndex,
            currentPosition = p.currentPosition.coerceAtLeast(0), totalDuration = duration,
            isPlaying = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING),
            playbackSpeed = p.playbackParameters.speed, isShuffleOn = p.shuffleModeEnabled, repeatMode = p.repeatMode)
    }

    private fun beginListening(song: Song?) {
        trackedSong = song; listenedMs = 0; recordedPlay = false
        resetEvent(System.currentTimeMillis())
        lastSample = SystemClock.elapsedRealtime(); wasPlaying = player?.isPlaying == true
    }
    private fun sampleListening() {
        val now = SystemClock.elapsedRealtime()
        val wallNow = System.currentTimeMillis()
        if (wasPlaying && trackedSong != null) {
            val duration = (now - lastSample).coerceIn(0, 7L * 86_400_000)
            listenedMs += duration
            val zone = java.time.ZoneId.systemDefault()
            listeningSlices(wallNow - duration, duration, zone).forEach { slice ->
                val day = java.time.Instant.ofEpochMilli(slice.startedAt).atZone(zone).toLocalDate()
                if (eventListenedMs > 0 && day != java.time.Instant.ofEpochMilli(eventStarted).atZone(zone).toLocalDate()) {
                    flushListening(); resetEvent(slice.startedAt)
                }
                if (eventListenedMs == 0L) eventStarted = slice.startedAt
                eventListenedMs += slice.durationMs
            }
        }
        val playing = player?.isPlaying == true
        if (wasPlaying && !playing) { flushListening(); resetEvent(wallNow) }
        lastSample = now; wasPlaying = playing
        val song = trackedSong
        if (!recordedPlay && listenedMs >= 10_000 && song != null && song.id > 0) {
            recordedPlay = true
            app.applicationScope.launch { app.database.playlistDao().recordSongPlayed(song.id, System.currentTimeMillis()) }
        }
    }
    private fun resetEvent(start: Long) { eventId = UUID.randomUUID().toString(); eventStarted = start; eventListenedMs = 0 }
    private fun flushListening() {
        val song = trackedSong ?: return
        if (eventListenedMs <= 0) return
        val event = ListeningEvent(eventId, song.id, song.title, song.artist, eventStarted, eventListenedMs)
        app.applicationScope.launch { app.database.featureDao().recordEvent(event) }
    }
    private fun persistPlayback() {
        if (!restored) return
        val p = player ?: return
        val snapshot = if (features.resumePlayback && p.mediaItemCount > 0) PlaybackSnapshot(
            _playerState.value.queue.map { it.id }, p.currentMediaItemIndex.coerceAtLeast(0), p.currentPosition.coerceAtLeast(0), p.shuffleModeEnabled, p.repeatMode, AudioEngine.order(p)) else null
        app.persistPlayback(snapshot)
    }

    private fun ensureEqualizer() {
        val id = player?.audioSessionId ?: 0
        if (id <= 0 || id == audioSessionId) return
        audioSessionId = id
        eqObserver?.cancel(); equalizerManager?.release()
        val manager = EqualizerManager(id).also { it.restore(decodeEqualizer(savedEqualizer)) }
        equalizerManager = manager; _equalizerManagerState.value = manager
        eqObserver = serviceScope.launch { manager.equalizerState.drop(1).collect { state ->
            savedEqualizer = state.encode()
            app.persistEqualizer(savedEqualizer)
        } }
    }

    private fun startTimer(seconds: Long, endOfTrack: Boolean) {
        sleepTimerJob?.cancel(); crossfade.cancel(); sleepVolume = 1f; crossfade.applyVolumes()
        _sleepTimerState.value = SleepTimerState(true, seconds, endOfTrack)
        if (endOfTrack) return
        sleepTimerJob = serviceScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000); remaining--
                _sleepTimerState.value = SleepTimerState(true, remaining, false)
                sleepVolume = if (remaining in 1..20) remaining / 20f else 1f
                crossfade.applyVolumes()
            }
            player?.pause(); stopTimer()
        }
    }
    private fun stopTimer() { sleepTimerJob?.cancel(); sleepTimerJob = null; sleepVolume = 1f; crossfade.applyVolumes(); _sleepTimerState.value = SleepTimerState() }
    private fun finishEndOfTrack() { if (_sleepTimerState.value.isEndOfTrack) { player?.pause(); stopTimer() } }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        sampleListening(); flushListening(); crossfade.cancel()
        beginListening(songFrom(mediaItem))
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) finishEndOfTrack()
        publishState(); persistPlayback(); AuraPlayAppWidgetProvider.updateAllWidgets(this)
    }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_REMOVE) { crossfade.cancel(); persistPlayback() }
    }
    override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) finishEndOfTrack() }
    override fun onTimelineChanged(timeline: Timeline, reason: Int) { if (::crossfade.isInitialized && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) crossfade.cancel(); updateQueue(); persistPlayback() }
    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) updateQueue()
        sampleListening(); publishState(); ensureEqualizer()
        if (!player.playWhenReady && ::crossfade.isInitialized) crossfade.cancel()
        if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            flushListening(); persistPlayback(); AuraPlayAppWidgetProvider.updateAllWidgets(this)
        }
    }
    override fun onPlayerError(error: PlaybackException) {
        android.util.Log.e("MusicService", "Playback failed: ${error.errorCodeName}, item=${player?.currentMediaItem?.mediaId}, " +
            "position=${player?.currentPosition}, format=${player?.audioFormat}", error)
        crossfade.cancel()
        publishState()
    }

    private fun attachPlaybackRecovery(player: Player) {
        playbackRecovery = PlaybackRecovery(player, serviceScope, ::showPlaybackError).also(player::addListener)
    }

    private fun showPlaybackError(error: PlaybackException) {
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Audio access was denied. Check AuraPlay's music permission in Settings."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This audio file is unavailable. Rescan your music library."
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "Audio output is unavailable. Try playing again or reconnect your headphones."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This audio format could not be decoded. Try another song."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This audio file is damaged or uses an unsupported format. Try another song."
            else -> "Playback failed. Try playing again."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
    override fun onTaskRemoved(rootIntent: Intent?) { if (player?.playWhenReady != true || player?.mediaItemCount == 0) stopSelf() }
    override fun onDestroy() {
        sampleListening(); flushListening(); persistPlayback()
        equalizerManager?.let { app.persistEqualizer(it.equalizerState.value.encode()) }
        if (::crossfade.isInitialized) crossfade.cancel()
        playbackRecovery?.release()
        eqObserver?.cancel(); equalizerManager?.release(); oldEqualizer?.release()
        _equalizerManagerState.value = null
        serviceScope.cancel(); mediaSession?.release(); mediaSession = null
        player?.release(); audio = null; instance = null
        _playerState.value = PlayerState(); _sleepTimerState.value = SleepTimerState()
        AuraPlayAppWidgetProvider.updateAllWidgets(this)
        super.onDestroy()
    }
}
