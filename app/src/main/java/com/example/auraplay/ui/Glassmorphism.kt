package com.example.auraplay.ui

import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.auraplay.ui.theme.LocalThemeIsDark

// 💫 Dynamic glassmorphic styling parameters
data class GlassCardColors(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color,
    val subTextColor: Color,
    val accentColor: Color
)

fun resolveAccentColor(accentTheme: String, dynamicColor: Color? = null): Color {
    return when (accentTheme.uppercase()) {
        "MINT" -> Color(0xFFC4E6BB)
        "DYNAMIC" -> dynamicColor?.let { sanitizeDynamicColor(it) } ?: Color(0xFFC4E6BB)
        "PURPLE" -> Color(0xFFC084FC)
        "CYAN" -> Color(0xFF38BDF8)
        "SUNSET" -> Color(0xFFFB923C)
        "EMERALD" -> Color(0xFF34D399)
        "GOLD" -> Color(0xFFFBBF24)
        else -> dynamicColor?.let { sanitizeDynamicColor(it) } ?: Color(0xFFC4E6BB)
    }
}

/**
 * Ensures any dynamic color is vivid and clearly visible.
 * Filters out gray/white/washed-out tones and boosts saturation.
 */
fun sanitizeDynamicColor(color: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsl
    )

    // If color is gray (saturation < 0.35) or too close to white/black
    if (hsl[1] < 0.35f || hsl[2] > 0.78f || hsl[2] < 0.18f) {
        if (hsl[1] >= 0.18f) {
            hsl[1] = hsl[1].coerceIn(0.70f, 0.98f)
            hsl[2] = hsl[2].coerceIn(0.48f, 0.62f)
            val boosted = ColorUtils.HSLToColor(hsl)
            return Color(boosted)
        }
        // Fallback for purely monochrome/grayscale covers
        return Color(0xFFC4E6BB)
    }

    // Boost saturation and keep in ideal lightness range
    hsl[1] = hsl[1].coerceIn(0.68f, 0.98f)
    hsl[2] = hsl[2].coerceIn(0.48f, 0.62f)
    val boosted = ColorUtils.HSLToColor(hsl)
    return Color(boosted)
}

@Composable
fun glassCardColors(
    darkTheme: Boolean = LocalThemeIsDark.current,
    customAccent: Color? = null,
    isPureBlack: Boolean = false
): GlassCardColors {
    val theme = MaterialTheme.colorScheme
    val accent = customAccent?.let {
        if (darkTheme || it == theme.primary) it else lerp(it, Color(0xFF102012), 0.55f)
    } ?: theme.primary
    return GlassCardColors(
        backgroundColor = theme.surface,
        borderColor = theme.outlineVariant.copy(alpha = 0.5f),
        textColor = theme.onSurface,
        subTextColor = if (darkTheme) Color(0xFFB6BDB0) else Color(0xFF53604F),
        accentColor = accent
    )
}

// 🪄 Glassmorphic Modifier with Guaranteed Clipped Borders and Dynamic Shadows
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    backgroundColor: Color = Color.White.copy(alpha = 0.06f),
    shadowElevation: Dp = 0.dp
): Modifier = this
    .then(
        if (shadowElevation > 0.dp) {
            Modifier.shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
        } else Modifier
    )
    .clip(shape)
    .background(backgroundColor)
    .border(borderWidth, borderColor, shape)

/**
 * Tactile spring press feedback with bounded shape ripple.
 * Fixes the square ripple box issue on circular or rounded buttons and provides smooth physical bounce.
 */
@Composable
fun Modifier.bouncingClickable(
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    scaleDown: Float = 0.88f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .pressFeedback(interactionSource, enabled, scaleDown)
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(bounded = true),
            enabled = enabled,
            onClick = onClick
        )
}

/** A quiet, static canvas: no full-screen animated layers during library scrolling. */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    customAccent: Color? = null,
    isPureBlack: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        content = content
    )
}
