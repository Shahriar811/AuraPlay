package com.example.auraplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.auraplay.AuraPlayApplication
import com.example.auraplay.MainActivity
import com.example.auraplay.R
import com.example.auraplay.service.MusicService
import com.example.auraplay.features.playOrRetry
import com.example.auraplay.features.seekToNextOrRetry
import com.example.auraplay.features.seekToPreviousOrRetry
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuraPlayAppWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.auraplay.widget.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.auraplay.widget.ACTION_PREV"
        const val ACTION_NEXT = "com.example.auraplay.widget.ACTION_NEXT"
        private val cache = LruCache<String, Bitmap>(8)
        private val artworkJobs = ConcurrentHashMap<Int, Job>()
        private val generations = ConcurrentHashMap<Int, Long>()
        private val nextGeneration = java.util.concurrent.atomic.AtomicLong()
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, AuraPlayAppWidgetProvider::class.java)).forEach { updateAppWidget(context, manager, it) }
        }
        fun updateAppWidget(context: Context, manager: AppWidgetManager, id: Int) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Handler(Looper.getMainLooper()).post { updateAppWidget(context, manager, id) }; return
            }
            val generation = nextGeneration.incrementAndGet()
            generations[id] = generation
            val app = context.applicationContext as AuraPlayApplication
            val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 70)
            val requested = WidgetStyles.get(context, id)
            val style = if (height < 120) WidgetStyle.COMPACT else if (height < 180 && requested == WidgetStyle.ARTWORK) WidgetStyle.STANDARD else requested
            val layout = when (style) { WidgetStyle.COMPACT -> R.layout.widget_auraplay; WidgetStyle.STANDARD -> R.layout.widget_auraplay_large; WidgetStyle.ARTWORK -> R.layout.widget_auraplay_artwork }
            val views = RemoteViews(context.packageName, layout)
            val state = MusicService.playerState.value
            val song = state.currentSong
            views.setTextViewText(R.id.widget_title, song?.title ?: "AuraPlay")
            views.setTextViewText(R.id.widget_artist, song?.artist ?: "Tap Play to resume")
            views.setImageViewResource(R.id.widget_btn_play_pause, if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            views.setContentDescription(R.id.widget_btn_play_pause, if (state.isPlaying) "Pause" else "Play")
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, open)
            listOf(R.id.widget_btn_play_pause to ACTION_PLAY_PAUSE, R.id.widget_btn_prev to ACTION_PREV, R.id.widget_btn_next to ACTION_NEXT).forEachIndexed { index, (button, action) ->
                views.setOnClickPendingIntent(button, PendingIntent.getBroadcast(context, index + 1, Intent(context, AuraPlayAppWidgetProvider::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            val uri = song?.albumArtUri
            val cached = uri?.let(cache::get)
            if (cached != null) views.setImageViewBitmap(R.id.widget_album_art, cached) else views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_placeholder)
            manager.updateAppWidget(id, views)
            artworkJobs.remove(id)?.cancel()
            if (uri != null && cached == null) {
                artworkJobs[id] = app.applicationScope.launch {
                    try {
                        val result = app.imageLoader.execute(ImageRequest.Builder(app).data(uri).size(256).allowHardware(false).build())
                        if (result is SuccessResult && isActive) {
                            val bitmap = result.drawable.toBitmap()
                            cache.put(uri, bitmap)
                            withContext(Dispatchers.Main.immediate) {
                                if (generations[id] == generation && MusicService.playerState.value.currentSong?.albumArtUri == uri)
                                    manager.partiallyUpdateAppWidget(id, RemoteViews(context.packageName, layout).apply { setImageViewBitmap(R.id.widget_album_art, bitmap) })
                            }
                        }
                    } catch (_: Exception) { /* The placeholder remains usable when artwork is unavailable. */ }
                }
            }
        }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { updateAppWidget(context, manager, it) } }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { updateAppWidget(context, manager, id) }
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetStyles.remove(context, it); generations.remove(it); artworkJobs.remove(it)?.cancel() } }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in listOf(ACTION_PLAY_PAUSE, ACTION_PREV, ACTION_NEXT)) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
            try {
                withTimeout(8000) {
                    val controller = suspendCancellableCoroutine<MediaController> { continuation ->
                        future.addListener({ if (continuation.isActive) try { continuation.resume(future.get()) } catch (error: Exception) { continuation.resumeWithException(error) } }, ContextCompat.getMainExecutor(context))
                    }
                    MusicService.awaitRestoration()
                    when (intent.action) {
                        ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.playOrRetry()
                        ACTION_PREV -> controller.seekToPreviousOrRetry()
                        ACTION_NEXT -> controller.seekToNextOrRetry()
                    }
                    delay(250)
                    updateAllWidgets(context)
                }
            } catch (_: Exception) { /* App remains accessible from the widget body. */ }
            finally { MediaController.releaseFuture(future); pending.finish(); scope.cancel() }
        }
    }
}
