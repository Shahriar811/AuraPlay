package com.example.auraplay

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/** Cheap change detection across primary storage AND removable volumes. Call off the main thread. */
internal fun mediaStoreScanStamp(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return try {
        val volumes = MediaStore.getExternalVolumeNames(context).sorted()
        if (volumes.isEmpty()) return null
        val entries = ArrayList<String>(volumes.size)
        for (volume in volumes) {
            val version = MediaStore.getVersion(context, volume) ?: return null
            val generation = MediaStore.getGeneration(context, volume)
            entries.add("$volume:${version.length}:$version:$generation")
        }
        entries.joinToString(";")
    } catch (_: Exception) {
        // Unsupported providers and older Android versions always take the safe full-scan path.
        null
    }
}

internal fun canReuseLibraryScan(current: String?, saved: String, hasCachedSongs: Boolean): Boolean =
    hasCachedSongs && !current.isNullOrBlank() && current == saved
