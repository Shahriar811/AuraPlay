package com.example.auraplay

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.auraplay.data.AppDatabase
import com.example.auraplay.features.PlaybackSnapshot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class AuraPlayApplication : Application(), ImageLoaderFactory {
    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }
    private val playbackWrites = Channel<PlaybackSnapshot?>(Channel.CONFLATED)
    private val equalizerWrites = Channel<String>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { for (snapshot in playbackWrites) {
            try { settingsDataStore.savePlaybackSnapshot(snapshot?.encode().orEmpty()) }
            catch (error: java.io.IOException) { android.util.Log.w("AuraPlay", "Could not save playback", error) }
        } }
        applicationScope.launch { for (json in equalizerWrites) {
            try { settingsDataStore.saveEqualizer(json) }
            catch (error: java.io.IOException) { android.util.Log.w("AuraPlay", "Could not save equalizer", error) }
        } }
    }
    fun persistPlayback(snapshot: PlaybackSnapshot?) { playbackWrites.trySend(snapshot) }
    fun persistEqualizer(json: String) { equalizerWrites.trySend(json) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
