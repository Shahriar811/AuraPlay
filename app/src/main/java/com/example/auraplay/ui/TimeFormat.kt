package com.example.auraplay.ui

import java.util.Locale

fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}
