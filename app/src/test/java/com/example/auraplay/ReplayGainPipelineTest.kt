package com.example.auraplay

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import com.example.auraplay.features.ReplayGainProcessor
import com.google.common.collect.ImmutableList
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReplayGainPipelineTest {
    private fun pipeline(gain: Double = 1.0): AudioProcessingPipeline {
        val processor = ReplayGainProcessor().apply { this.gain = gain }
        return AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(processor)).apply {
            configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
            flush()
        }
    }

    @Test fun emptyStartupBufferDoesNotFailBeforeFirstAudioFrame() {
        val pipeline = pipeline()
        assertFalse(pipeline.output.hasRemaining())
        pipeline.queueInput(AudioProcessor.EMPTY_BUFFER)
        assertFalse(pipeline.output.hasRemaining())
    }

    @Test fun unityGainPlaysThroughTheActualMedia3Pipeline() {
        val pipeline = pipeline()
        assertFalse(pipeline.output.hasRemaining())
        val samples = shortArrayOf(0, 1000, -1000, 32767, -32768, 42)
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach(input::putShort); input.flip()
        pipeline.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = pipeline.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(samples.size * 2, output.remaining())
        samples.forEach { assertEquals(it, output.short) }
        assertFalse(pipeline.output.hasRemaining())
        pipeline.queueEndOfStream()
        assertFalse(pipeline.output.hasRemaining())
        assertTrue(pipeline.isEnded)
    }

    @Test fun resetPipelineAcceptsEmptyDrainBeforeTheNextSong() {
        val pipeline = pipeline()
        repeat(3) {
            assertFalse(pipeline.output.hasRemaining())
            val input = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN).apply { putShort(1234); putShort(-1234); flip() }
            pipeline.queueInput(input)
            val output = pipeline.output.order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(1234.toShort(), output.short); assertEquals((-1234).toShort(), output.short)
            pipeline.queueEndOfStream()
            assertFalse(pipeline.output.hasRemaining()); assertTrue(pipeline.isEnded)
            pipeline.reset()
            pipeline.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)); pipeline.flush()
        }
    }

    @Test fun gainAndTrackChangesDrainWithoutCorruptingAudio() {
        val pipeline = pipeline(2.0)
        repeat(2) {
            val input = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(30000); putShort(-30000); flip()
            }
            pipeline.queueInput(input)
            val output = pipeline.output.order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(32767.toShort(), output.short)
            assertEquals((-32768).toShort(), output.short)
            pipeline.queueEndOfStream()
            assertFalse(pipeline.output.hasRemaining())
            assertTrue(pipeline.isEnded)
            pipeline.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
            pipeline.flush()
            assertFalse(pipeline.output.hasRemaining())
        }
    }
}
