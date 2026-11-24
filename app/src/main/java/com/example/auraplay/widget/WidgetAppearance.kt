package com.example.auraplay.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.auraplay.R

internal fun widgetLayout(style: WidgetStyle): Int = when (style) {
    WidgetStyle.COMPACT -> R.layout.widget_auraplay
    WidgetStyle.STANDARD -> R.layout.widget_auraplay_large
    WidgetStyle.SQUARE -> R.layout.widget_auraplay_square
    WidgetStyle.ARTWORK -> R.layout.widget_auraplay_artwork
}

internal fun applyWidgetShape(views: RemoteViews, shape: WidgetShape, style: WidgetStyle) {
    views.setInt(android.R.id.background, "setBackgroundResource", shape.background)
    if (style == WidgetStyle.ARTWORK) {
        views.setInt(R.id.widget_artwork_shade, "setBackgroundResource", shape.shade)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetAppearanceOptions(
    style: WidgetStyle,
    shape: WidgetShape,
    onStyleChange: (WidgetStyle) -> Unit,
    onShapeChange: (WidgetShape) -> Unit
) {
    WidgetAppearancePreview(style, shape)
    Text("Layout", style = MaterialTheme.typography.titleMedium)
    WidgetStyle.entries.forEach { option ->
        OutlinedCard(onClick = { onStyleChange(option) }, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(option.title) },
                supportingContent = { Text(option.description) },
                trailingContent = { RadioButton(option == style, onClick = null) }
            )
        }
    }
    Text("Corners", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetShape.entries.forEach { option ->
            FilterChip(selected = option == shape, onClick = { onShapeChange(option) }, label = { Text(option.title) })
        }
    }
    Text("Hold a widget on your home screen to resize or configure it. Resize existing widgets to a 2×2 slot for the Square layout.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WidgetAppearancePreview(style: WidgetStyle, shape: WidgetShape) {
    val height = when (style) {
        WidgetStyle.COMPACT -> 88.dp
        WidgetStyle.STANDARD -> 160.dp
        WidgetStyle.SQUARE -> 200.dp
        WidgetStyle.ARTWORK -> 216.dp
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = (if (style == WidgetStyle.SQUARE) Modifier.width(200.dp) else Modifier.fillMaxWidth()).height(height),
            factory = { FrameLayout(it).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS } },
            update = { parent ->
                parent.removeAllViews()
                val views = RemoteViews(parent.context.packageName, widgetLayout(style))
                applyWidgetShape(views, shape, style)
                parent.addView(views.apply(parent.context, parent))
            }
        )
    }
}
