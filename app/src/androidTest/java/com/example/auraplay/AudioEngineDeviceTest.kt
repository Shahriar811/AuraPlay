package com.example.auraplay

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auraplay.data.Song
import com.example.auraplay.features.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioEngineDeviceTest {
    @Test fun skippingAnUnavailableSongRestartsPlaybackForAppAndSessionControls() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val playable = File(context.cacheDir, "recovery-${System.nanoTime()}.wav")
        val missing = File(context.cacheDir, "missing-${System.nanoTime()}.wav")
        writeSilence(playable, 4)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var audio: AudioPlayer? = null
        var recovery: PlaybackRecovery? = null
        var failure: PlaybackException? = null
        try {
            withContext(Dispatchers.Main) {
                audio = AudioEngine.create(context, false) { "OFF" }
                audio!!.player.volume = 0f
                recovery = PlaybackRecovery(audio!!.player, scope) { failure = it }.also(audio!!.player::addListener)
            }
            for (sessionSeek in listOf(false, true)) {
                withContext(Dispatchers.Main) {
                    failure = null
                    audio!!.player.setMediaItems(listOf(missing, playable).mapIndexed { index, file ->
                        Song(index + 1L, "Recovery track $index", "Test", albumArtUri = null,
                            duration = 4000, data = Uri.fromFile(file).toString()).toMediaItem()
                    })
                    audio!!.player.prepare()
                    audio!!.player.play()
                }
                withTimeout(6000) {
                    while (withContext(Dispatchers.Main) { failure == null }) delay(25)
                }
                withContext(Dispatchers.Main) {
                    assertEquals(Player.STATE_IDLE, audio!!.player.playbackState)
                    assertNotNull(audio!!.player.playerError)
                    if (sessionSeek) audio!!.player.seekToNext() else audio!!.player.seekToNextOrRetry()
                }
                withTimeout(6000) {
                    while (!withContext(Dispatchers.Main) {
                        audio!!.player.isPlaying && audio!!.player.currentPosition >= 200
                    }) delay(25)
                }
                withContext(Dispatchers.Main) {
                    assertEquals(1, audio!!.player.currentMediaItemIndex)
                    assertEquals(2, audio!!.player.mediaItemCount)
                    assertNull(audio!!.player.playerError)
                }
            }
        } finally {
            withContext(Dispatchers.Main) { recovery?.release(); audio?.player?.release(); scope.cancel() }
            playable.delete()
        }
    }

    @Test fun pcmProcessorScalesRealBuffersAndClipsWithoutOverflow() {
        val processor = ReplayGainProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)); processor.flush()
        processor.gain = 2.0
        val input = ByteBuffer.allocateDirect(6).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(1000); putShort(30000); putShort(-30000); flip() }
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2000.toShort(), output.short); assertEquals(32767.toShort(), output.short); assertEquals((-32768).toShort(), output.short)
        assertFalse(input.hasRemaining()); processor.reset()
    }

    @Test fun crossfadeAdvancesIncomingDecoderBeforeOutgoingTrackEnds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = listOf(File(context.cacheDir, "fade-a-${System.nanoTime()}.wav"), File(context.cacheDir, "fade-b-${System.nanoTime()}.wav"))
        files.forEach { writeSilence(it, 4) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var current: AudioPlayer? = null
        var engine: CrossfadeEngine? = null
        var swapped = false
        var finished = false
        var outgoingPosition = 0L
        var incomingPosition = 0L
        try {
            withContext(Dispatchers.Main) {
                current = AudioEngine.create(context, false) { "OFF" }
                val tracks = files.mapIndexed { index, file -> Song(index + 1L, "Track $index", "Test", albumArtUri = null, duration = 4000, data = Uri.fromFile(file).toString()).toMediaItem() }
                current!!.player.setMediaItems(tracks); current!!.player.volume = 0f
                engine = CrossfadeEngine(context, scope, { current }, { FeatureSettings(crossfadeSeconds = 1) }, { true }, { 0f }, { incoming ->
                    outgoingPosition = current!!.player.currentPosition; current = incoming; swapped = true
                }, { finished = true })
                current!!.player.prepare(); current!!.player.play()
            }
            withTimeout(12000) {
                while (!finished) {
                    withContext(Dispatchers.Main) {
                        engine!!.tick()
                        if (swapped) incomingPosition = maxOf(incomingPosition, current!!.player.currentPosition)
                    }
                    delay(25)
                }
            }
            withContext(Dispatchers.Main) {
                assertTrue(swapped); assertTrue(outgoingPosition in 2500..3900)
                assertTrue(incomingPosition >= 500); assertEquals(1, current!!.player.currentMediaItemIndex)
                assertEquals(2, current!!.player.mediaItemCount)
            }
        } finally {
            withContext(Dispatchers.Main) { engine?.cancel(); current?.player?.release(); scope.cancel() }
            files.forEach { it.delete() }
        }
    }

    private fun writeSilence(file: File, seconds: Int) {
        val length = 44100 * seconds * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(length + 36); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(44100); putInt(88200); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(length)
        }.array()
        file.outputStream().use { it.write(header); it.write(ByteArray(length)) }
    }
}
