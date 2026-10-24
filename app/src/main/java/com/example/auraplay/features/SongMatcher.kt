package com.example.auraplay.features

import com.example.auraplay.data.Song

/** Portable matches require a unique reference. MediaStore IDs differ across devices. */
class SongMatcher(library: List<Song>) {
    private val byPath = library.filter { isFilePath(it.data) }.groupBy { it.data }
    private val byInfo = library.groupBy { Triple(it.title, it.artist, it.duration) }
    private val byFile = library.filter { isFilePath(it.data) }.groupBy { it.data.substringAfterLast('/') to it.duration }

    fun match(path: String, title: String, artist: String, duration: Long): Song? =
        (if (isFilePath(path)) byPath[path]?.singleOrNull() else null)
            ?: (if (title.isNotBlank()) byInfo[Triple(title, artist, duration)]?.singleOrNull() else null)
            ?: (if (isFilePath(path) && path.substringAfterLast('/').isNotBlank()) byFile[path.substringAfterLast('/') to duration]?.singleOrNull() else null)

    private fun isFilePath(path: String) = path.startsWith("/") || path.startsWith("file:")
}
