package com.example.auraplay

import com.example.auraplay.ui.formatTime
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun formatTime_negativeInput_returnsZero() {
        assertEquals("00:00", formatTime(-500L))
        assertEquals("00:00", formatTime(-1L))
    }

    @Test
    fun formatTime_zero_returnsZero() {
        assertEquals("00:00", formatTime(0L))
    }

    @Test
    fun formatTime_minutesAndSeconds() {
        assertEquals("01:05", formatTime(65_000L))
        assertEquals("03:45", formatTime(225_000L))
    }

    @Test
    fun formatTime_hours_returnsThreePartString() {
        assertEquals("01:00:00", formatTime(3600_000L))
        assertEquals("01:15:30", formatTime(4530_000L))
    }
}