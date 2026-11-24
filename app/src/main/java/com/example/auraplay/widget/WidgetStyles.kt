package com.example.auraplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.auraplay.R

enum class WidgetStyle(val title: String, val description: String) {
    COMPACT("Compact", "A slim row with song details and playback controls."),
    STANDARD("Large", "A larger cover, song details and roomy controls. Resize to two rows."),
    SQUARE("Square", "Artwork, song details and controls in a 2×2 home screen slot."),
    ARTWORK("Artwork", "Cover art fills the widget with controls overlaid. Resize to three rows.")
}

enum class WidgetShape(val title: String, val radiusDp: Int, val background: Int, val shade: Int) {
    SQUARE("Straight", 0, R.drawable.widget_background_square, R.drawable.widget_artwork_shade_square),
    SOFT("Soft", 12, R.drawable.widget_background_soft, R.drawable.widget_artwork_shade_soft),
    ROUNDED("Rounded", 24, R.drawable.widget_background, R.drawable.widget_artwork_shade),
    EXTRA_ROUNDED("Extra rounded", 40, R.drawable.widget_background_extra_rounded, R.drawable.widget_artwork_shade_extra_rounded)
}

object WidgetStyles {
    private fun prefs(context: Context) = context.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)
    fun default(context: Context) = parse(prefs(context).getString("default", "COMPACT"))
    fun get(context: Context, id: Int): WidgetStyle {
        val saved = prefs(context).getString("widget_$id", null)
        if (saved != null) return parse(saved)
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider
        return if (provider == ComponentName(context, AuraPlaySquareWidgetProvider::class.java)) WidgetStyle.SQUARE else default(context)
    }
    fun defaultShape(context: Context) = parseShape(prefs(context).getString("default_shape", "ROUNDED"))
    fun shape(context: Context, id: Int) = parseShape(prefs(context).getString("widget_shape_$id", defaultShape(context).name))
    fun set(context: Context, id: Int, style: WidgetStyle) { prefs(context).edit().putString("widget_$id", style.name).apply() }
    fun setShape(context: Context, id: Int, shape: WidgetShape) { prefs(context).edit().putString("widget_shape_$id", shape.name).apply() }
    fun setAppearance(context: Context, id: Int, style: WidgetStyle, shape: WidgetShape) {
        prefs(context).edit().putString("widget_$id", style.name).putString("widget_shape_$id", shape.name).apply()
    }
    fun remove(context: Context, id: Int) { prefs(context).edit().remove("widget_$id").remove("widget_shape_$id").apply() }
    fun setDefault(context: Context, style: WidgetStyle) {
        val editor = prefs(context).edit().putString("default", style.name)
        ids(context).forEach { editor.putString("widget_$it", style.name) }
        editor.apply(); AuraPlayAppWidgetProvider.updateAllWidgets(context)
    }
    fun setDefaultShape(context: Context, shape: WidgetShape) {
        val editor = prefs(context).edit().putString("default_shape", shape.name)
        ids(context).forEach { editor.putString("widget_shape_$it", shape.name) }
        editor.apply()
        AuraPlayAppWidgetProvider.updateAllWidgets(context)
    }
    internal fun ids(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(AuraPlayAppWidgetProvider::class.java, AuraPlaySquareWidgetProvider::class.java)
            .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
    }
    internal fun provider(context: Context, style: WidgetStyle) = ComponentName(context,
        if (style == WidgetStyle.SQUARE) AuraPlaySquareWidgetProvider::class.java else AuraPlayAppWidgetProvider::class.java)
    private fun parse(value: String?) = WidgetStyle.entries.firstOrNull { it.name == value } ?: WidgetStyle.COMPACT
    private fun parseShape(value: String?) = WidgetShape.entries.firstOrNull { it.name == value } ?: WidgetShape.ROUNDED
}
