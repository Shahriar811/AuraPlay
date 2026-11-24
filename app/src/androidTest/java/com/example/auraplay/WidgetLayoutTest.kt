package com.example.auraplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auraplay.widget.WidgetShape
import com.example.auraplay.widget.WidgetStyle
import com.example.auraplay.widget.WidgetStyles
import com.example.auraplay.widget.applyWidgetShape
import com.example.auraplay.widget.widgetLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class WidgetLayoutTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun compactControlsStayCenteredWithArtworkAtDifferentHeights() = onMain {
        for (height in listOf(96, 144)) {
            val widget = inflateWidget(WidgetStyle.COMPACT, WidgetShape.ROUNDED, 320, height)
            val artwork = widget.boundsOf(R.id.widget_album_art)
            val controls = assertPlaybackBounds(widget)
            controls.forEach { bounds ->
                assertTrue("Compact controls must stay vertically centered with the cover at ${height}dp",
                    abs(artwork.exactCenterY() - bounds.exactCenterY()) <= 1f)
            }
            if (height == 96) savePreview(widget, "compact-rounded.png")
        }
    }

    @Test fun narrowControlsFitTheSmallestSupportedHost() = onMain {
        val widget = inflateWidget(WidgetStyle.COMPACT, WidgetShape.ROUNDED, 160, 70,
            layoutId = R.layout.widget_auraplay_narrow)
        val controls = assertPlaybackBounds(widget)
        val title = widget.boundsOf(R.id.widget_title)
        assertTrue("The narrow title must fit inside the widget", contains(widget, title))
        assertTrue("The narrow title must stay above playback controls", title.bottom <= controls.first().top)
    }

    @Test fun squareControlsAndMetadataFitTheMinimumWidgetSize() = onMain {
        WidgetShape.entries.forEach { shape ->
            val widget = inflateWidget(WidgetStyle.SQUARE, shape, 160, 160)
            val controls = assertPlaybackBounds(widget)
            val artwork = widget.boundsOf(R.id.widget_album_art)
            val title = widget.boundsOf(R.id.widget_title)
            val artist = widget.boundsOf(R.id.widget_artist)
            assertTrue("The square widget must retain visible artwork", artwork.height() > 0)
            assertTrue("Artwork must leave room for the title", artwork.bottom <= title.top)
            assertTrue("Title and artist must not overlap", title.bottom <= artist.top)
            assertTrue("Metadata must stay above the controls", artist.bottom <= controls.first().top)
            assertTrue("The title must fit inside the widget", contains(widget, title))
            assertTrue("The artist must fit inside the widget", contains(widget, artist))
            if (shape == WidgetShape.SQUARE) savePreview(widget, "square-straight.png")
            if (shape == WidgetShape.ROUNDED) savePreview(widget, "square-rounded.png")
        }
    }

    @Test fun shapeSelectionUpdatesTheContainerAndArtworkOverlay() = onMain {
        WidgetStyle.entries.forEach { style ->
            WidgetShape.entries.forEach { shape ->
                val widget = inflateWidget(style, shape, 320, 220)
                val expectedRadius = shape.radiusDp * context.resources.displayMetrics.density
                val background = widget.background as GradientDrawable
                assertEquals("The selected corner shape must reach the RemoteViews container",
                    expectedRadius, background.cornerRadius, 1f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    assertTrue("The container must clip artwork to its selected outline", widget.clipToOutline)
                }
                if (style == WidgetStyle.ARTWORK) {
                    val shade = widget.findViewById<View>(R.id.widget_artwork_shade).background as GradientDrawable
                    assertEquals("Artwork shading must use the same corner radius",
                        expectedRadius, shade.cornerRadius, 1f)
                }
            }
        }
    }

    @Test fun perWidgetAppearanceSurvivesReconfigurationAndRemovalRestoresDefaults() {
        val widgetId = Int.MAX_VALUE - 42
        val prefs = context.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)
        val styleKey = "widget_$widgetId"
        val shapeKey = "widget_shape_$widgetId"
        val originalStyle = prefs.getString(styleKey, null)
        val originalShape = prefs.getString(shapeKey, null)
        val defaultStyle = WidgetStyles.default(context)
        val defaultShape = WidgetStyles.defaultShape(context)
        try {
            WidgetStyles.remove(context, widgetId)
            assertEquals(defaultStyle, WidgetStyles.get(context, widgetId))
            assertEquals(defaultShape, WidgetStyles.shape(context, widgetId))

            WidgetStyles.set(context, widgetId, WidgetStyle.SQUARE)
            WidgetStyles.setShape(context, widgetId, WidgetShape.SQUARE)
            val reconfiguredContext = ContextThemeWrapper(context, R.style.Theme_AuraPlay)
            assertEquals(WidgetStyle.SQUARE, WidgetStyles.get(reconfiguredContext, widgetId))
            assertEquals(WidgetShape.SQUARE, WidgetStyles.shape(reconfiguredContext, widgetId))

            WidgetStyles.set(context, widgetId, WidgetStyle.ARTWORK)
            WidgetStyles.setShape(context, widgetId, WidgetShape.EXTRA_ROUNDED)
            assertEquals(WidgetStyle.ARTWORK.name, prefs.getString(styleKey, null))
            assertEquals(WidgetShape.EXTRA_ROUNDED.name, prefs.getString(shapeKey, null))
            assertEquals(WidgetStyle.ARTWORK, WidgetStyles.get(reconfiguredContext, widgetId))
            assertEquals(WidgetShape.EXTRA_ROUNDED, WidgetStyles.shape(reconfiguredContext, widgetId))

            WidgetStyles.remove(context, widgetId)
            assertEquals(defaultStyle, WidgetStyles.get(context, widgetId))
            assertEquals(defaultShape, WidgetStyles.shape(context, widgetId))
        } finally {
            prefs.edit().apply {
                if (originalStyle == null) remove(styleKey) else putString(styleKey, originalStyle)
                if (originalShape == null) remove(shapeKey) else putString(shapeKey, originalShape)
            }.commit()
        }
    }

    private fun inflateWidget(
        style: WidgetStyle,
        shape: WidgetShape,
        widthDp: Int,
        heightDp: Int,
        layoutId: Int = widgetLayout(style)
    ): ViewGroup {
        val themed = ContextThemeWrapper(context, R.style.Theme_AuraPlay)
        val host = FrameLayout(themed)
        val views = RemoteViews(context.packageName, layoutId)
        applyWidgetShape(views, shape, style)
        val widget = views.apply(themed, host) as ViewGroup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Launchers honor a custom outline only when the background uses this standard ID.
            assertEquals("The launcher must recognize the widget's custom background", android.R.id.background, widget.id)
            assertTrue("The launcher must retain the widget's selected outline", widget.clipToOutline)
        }
        host.addView(widget)
        host.measure(View.MeasureSpec.makeMeasureSpec(px(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(heightDp), View.MeasureSpec.EXACTLY))
        host.layout(0, 0, host.measuredWidth, host.measuredHeight)
        return widget
    }

    private fun savePreview(widget: View, fileName: String) {
        val directory = File(context.cacheDir, "widget-layout-previews")
        assertTrue("Widget preview directory must be writable", directory.isDirectory || directory.mkdirs())
        val bitmap = Bitmap.createBitmap(widget.width, widget.height, Bitmap.Config.ARGB_8888)
        try {
            widget.draw(Canvas(bitmap))
            FileOutputStream(File(directory, fileName)).use { output ->
                assertTrue("Native widget preview must be saved", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertPlaybackBounds(widget: ViewGroup): List<Rect> {
        val buttons = listOf(R.id.widget_btn_prev, R.id.widget_btn_play_pause, R.id.widget_btn_next)
        val bounds = buttons.map { id ->
            val button = widget.findViewById<View>(id)
            assertEquals(View.VISIBLE, button.visibility)
            val rect = widget.boundsOf(id)
            assertEquals("Every playback action needs a 48dp touch target", px(48), rect.width())
            assertEquals("Every playback action needs a 48dp touch target", px(48), rect.height())
            assertTrue("Playback controls must remain inside the host", contains(widget, rect))
            rect
        }
        bounds.zipWithNext().forEach { (before, after) ->
            assertTrue("Playback touch targets must not overlap", before.right <= after.left)
            assertEquals("Playback icons must share one center line", before.exactCenterY(), after.exactCenterY(), 1f)
        }
        return bounds
    }

    private fun ViewGroup.boundsOf(id: Int): Rect {
        val view = findViewById<View>(id)
        val rect = Rect(0, 0, view.width, view.height)
        offsetDescendantRectToMyCoords(view, rect)
        return rect
    }

    private fun contains(widget: ViewGroup, rect: Rect) =
        rect.left >= 0 && rect.top >= 0 && rect.right <= widget.width && rect.bottom <= widget.height

    private fun px(dp: Int) = (dp * context.resources.displayMetrics.density).roundToInt()

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
}
