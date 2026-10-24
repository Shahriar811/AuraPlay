package com.example.auraplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

enum class WidgetStyle(val title: String, val description: String) {
    COMPACT("Compact", "A slim row with song details and playback controls."),
    STANDARD("Large", "A larger cover, song details and roomy controls. Resize to two rows."),
    ARTWORK("Artwork", "Cover art fills the widget with controls overlaid. Resize to three rows.")
}

object WidgetStyles {
    private fun prefs(context: Context) = context.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)
    fun default(context: Context) = parse(prefs(context).getString("default", "COMPACT"))
    fun get(context: Context, id: Int) = parse(prefs(context).getString("widget_$id", default(context).name))
    fun set(context: Context, id: Int, style: WidgetStyle) { prefs(context).edit().putString("widget_$id", style.name).apply() }
    fun remove(context: Context, id: Int) { prefs(context).edit().remove("widget_$id").apply() }
    fun setDefault(context: Context, style: WidgetStyle) {
        val editor = prefs(context).edit().putString("default", style.name)
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AuraPlayAppWidgetProvider::class.java)).forEach { editor.putString("widget_$it", style.name) }
        editor.apply(); AuraPlayAppWidgetProvider.updateAllWidgets(context)
    }
    private fun parse(value: String?) = WidgetStyle.entries.firstOrNull { it.name == value } ?: WidgetStyle.COMPACT
}
