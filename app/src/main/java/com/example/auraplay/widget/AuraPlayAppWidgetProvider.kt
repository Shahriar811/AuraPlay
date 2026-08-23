package com.example.auraplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.auraplay.MainActivity
import com.example.auraplay.R
import com.example.auraplay.service.MusicService

class AuraPlayAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.auraplay.widget.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.auraplay.widget.ACTION_PREV"
        const val ACTION_NEXT = "com.example.auraplay.widget.ACTION_NEXT"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, AuraPlayAppWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_auraplay)
            val playerState = MusicService.playerState.value
            val currentSong = playerState.currentSong

            if (currentSong != null) {
                views.setTextViewText(R.id.widget_title, currentSong.title)
                views.setTextViewText(R.id.widget_artist, currentSong.artist)
            } else {
                views.setTextViewText(R.id.widget_title, "AuraPlay")
                views.setTextViewText(R.id.widget_artist, "No music playing")
            }

            val playPauseIcon = if (playerState.isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon)

            // Intent to open MainActivity
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

            // Play/Pause button intent
            val playPauseIntent = Intent(context, AuraPlayAppWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            // Prev button intent
            val prevIntent = Intent(context, AuraPlayAppWidgetProvider::class.java).apply {
                action = ACTION_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

            // Next button intent
            val nextIntent = Intent(context, AuraPlayAppWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                3,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                MusicService.togglePlayPause()
            }
            ACTION_PREV -> {
                MusicService.seekToPrevious()
            }
            ACTION_NEXT -> {
                MusicService.seekToNext()
            }
        }
        updateAllWidgets(context)
    }
}
