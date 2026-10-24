package com.example.auraplay.features

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.metadata.flac.VorbisComment
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReplayGainProcessor : BaseAudioProcessor() {
    @Volatile var gain = 1.0
    override fun onConfigure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (input.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(input)
        return input
    }
    override fun queueInput(input: ByteBuffer) {
        // Media3 drains the pipeline with EMPTY_BUFFER before supplying the first frame.
        // BaseAudioProcessor initially shares that same buffer: put(input) would self-copy.
        if (!input.hasRemaining()) return
        val output = replaceOutputBuffer(input.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        input.order(ByteOrder.LITTLE_ENDIAN)
        val multiplier = gain
        if (multiplier == 1.0) { output.put(input); output.flip(); return }
        while (input.remaining() >= 2) output.putShort(scalePcm16(input.short, multiplier))
        output.flip()
    }
}

data class AudioPlayer(val player: ExoPlayer, val gainProcessor: ReplayGainProcessor)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioEngine {
    val attributes: AudioAttributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()
    fun create(context: Context, focus: Boolean, gainMode: () -> String): AudioPlayer {
        val processor = ReplayGainProcessor()
        val renderer = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(processor)).build()
        }
        renderer.setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(context, renderer).build().apply {
            setAudioAttributes(attributes, focus)
            setHandleAudioBecomingNoisy(true)
        }
        val audio = AudioPlayer(player, processor)
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { refreshGain(audio, gainMode()) }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { processor.gain = 1.0 }
        })
        return audio
    }

    fun refreshGain(audio: AudioPlayer, mode: String) {
        if (mode == "OFF") { audio.gainProcessor.gain = 1.0; return }
        val tags = mutableMapOf<String, String>()
        audio.player.currentTracks.groups.filter { it.isSelected }.forEach { group ->
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    val entry = metadata[j]
                    val pair = when (entry) {
                        is VorbisComment -> entry.key to entry.value
                        is TextInformationFrame -> entry.description.orEmpty() to entry.values.firstOrNull().orEmpty()
                        is InternalFrame -> entry.description to entry.text
                        else -> null
                    }
                    pair?.let { tags[it.first.uppercase(Locale.ROOT)] = it.second }
                }
            }
        }
        val number = Regex("[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")
        fun value(key: String): Double? = tags[key]?.let { number.find(it)?.value?.toDoubleOrNull() }
        val prefix = if (mode == "ALBUM" && tags.containsKey("REPLAYGAIN_ALBUM_GAIN")) "REPLAYGAIN_ALBUM" else "REPLAYGAIN_TRACK"
        audio.gainProcessor.gain = replayGainMultiplier(value("${prefix}_GAIN"), value("${prefix}_PEAK"))
    }

    fun order(player: ExoPlayer): List<Int> {
        val timeline = player.currentTimeline
        val count = player.mediaItemCount
        if (!player.shuffleModeEnabled || timeline.isEmpty) return (0 until count).toList()
        val result = ArrayList<Int>(count)
        val visited = BooleanArray(count)
        var index = timeline.getFirstWindowIndex(true)
        while (index in visited.indices && !visited[index]) {
            visited[index] = true
            result.add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        // Queue snapshots run on the player thread; avoid quadratic list membership scans.
        for (remaining in 0 until count) if (!visited[remaining]) result.add(remaining)
        return result
    }
}

/** Two decoders overlap only during a requested crossfade. The session follows the incoming player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CrossfadeEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val current: () -> AudioPlayer?,
    private val options: () -> FeatureSettings,
    private val allowed: () -> Boolean,
    private val baseVolume: () -> Float,
    private val swap: (AudioPlayer) -> Unit,
    private val tailReleased: () -> Unit
) {
    private var prepared: AudioPlayer? = null
    private var preparedFor: String? = null
    private var preparedIndex = -1
    private var tail: AudioPlayer? = null
    private var tailGuard: Player.Listener? = null
    private var fadeJob: Job? = null
    private var failedFor: String? = null
    private var progress = 1f

    fun tick() {
        val active = current() ?: return
        val p = active.player
        val settings = options()
        if (settings.crossfadeSeconds == 0 || !allowed() || !p.isPlaying || p.repeatMode == Player.REPEAT_MODE_ONE) {
            if (prepared != null || tail != null) cancel()
            return
        }
        if (tail != null || p.duration <= 1000 || p.nextMediaItemIndex == C.INDEX_UNSET) return
        val key = p.currentMediaItem?.mediaMetadata?.extras?.getString("queue_key") ?: p.currentMediaItem?.mediaId
        if (key == null || failedFor == key) return
        val remaining = ((p.duration - p.currentPosition) / p.playbackParameters.speed).toLong()
        val desired = minOf(settings.crossfadeSeconds * 1000L, (p.duration / p.playbackParameters.speed / 2).toLong())
        if (remaining > desired + 2000 || remaining <= 60) return
        if (prepared == null) {
            val next = AudioEngine.create(context, false) { options().replayGain }
            prepared = next; preparedFor = key; preparedIndex = p.nextMediaItemIndex
            next.player.volume = 0f
            next.player.setMediaItems((0 until p.mediaItemCount).map(p::getMediaItemAt), preparedIndex, 0L)
            next.player.setShuffleOrder(DefaultShuffleOrder(AudioEngine.order(p).toIntArray(), System.nanoTime()))
            next.player.shuffleModeEnabled = p.shuffleModeEnabled
            next.player.repeatMode = p.repeatMode
            next.player.playbackParameters = p.playbackParameters
            next.player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { failedFor = key; clearPrepared() }
            })
            next.player.prepare()
        }
        val incoming = prepared ?: return
        if (preparedFor != key || preparedIndex != p.nextMediaItemIndex) { clearPrepared(); return }
        if (remaining <= desired && incoming.player.playbackState == Player.STATE_READY) {
            val duration = minOf(remaining, desired, (incoming.player.duration.coerceAtLeast(0) / p.playbackParameters.speed / 2).toLong())
            if (duration < 100) { clearPrepared(); return }
            begin(active, incoming, duration)
        }
    }

    private fun begin(outgoing: AudioPlayer, incoming: AudioPlayer, duration: Long) {
        prepared = null; preparedFor = null; progress = 0f
        tail = outgoing
        tailGuard = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    incoming.player.pause(); cancel()
                }
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // Do not let the tail start a second copy of the incoming song.
                outgoing.player.pause(); outgoing.player.volume = 0f
            }
        }.also(outgoing.player::addListener)
        swap(incoming)
        incoming.player.play()
        val start = SystemClock.elapsedRealtime()
        fadeJob = scope.launch {
            while (progress < 1f && current() === incoming && tail === outgoing) {
                progress = ((SystemClock.elapsedRealtime() - start).toFloat() / duration).coerceIn(0f, 1f)
                applyVolumes()
                delay(25)
            }
            finishTail()
        }
    }

    fun applyVolumes() {
        val volume = baseVolume()
        if (tail == null) current()?.player?.volume = volume
        else {
            tail?.player?.volume = (cos(progress * Math.PI / 2) * volume).toFloat()
            current()?.player?.volume = (sin(progress * Math.PI / 2) * volume).toFloat()
        }
    }

    fun cancel() {
        fadeJob?.cancel(); fadeJob = null
        failedFor = null
        clearPrepared(); finishTail()
    }

    private fun clearPrepared() { val p = prepared; prepared = null; preparedFor = null; p?.player?.release() }
    private fun finishTail() {
        val old = tail
        tail = null; progress = 1f
        old?.let {
            tailGuard?.let(it.player::removeListener); tailGuard = null
            it.player.pause(); it.player.release()
            current()?.player?.setAudioAttributes(AudioEngine.attributes, true)
            tailReleased()
        }
        current()?.player?.volume = baseVolume()
    }
}
