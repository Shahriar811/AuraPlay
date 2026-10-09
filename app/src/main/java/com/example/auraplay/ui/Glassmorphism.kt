package com.example.auraplay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
        "DYNAMIC" -> dynamicColor?.let { sanitizeDynamicColor(it) } ?: Color(0xFFC084FC)
        "PURPLE" -> Color(0xFFC084FC)
        "CYAN" -> Color(0xFF38BDF8)
        "SUNSET" -> Color(0xFFFB923C)
        "EMERALD" -> Color(0xFF34D399)
        "GOLD" -> Color(0xFFFBBF24)
        else -> dynamicColor?.let { sanitizeDynamicColor(it) } ?: Color(0xFFC084FC)
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
        return Color(0xFFC084FC)
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
    val accent = customAccent ?: (if (darkTheme) Color(0xFFC084FC) else Color(0xFF7C3AED))
    return if (darkTheme) {
        GlassCardColors(
            backgroundColor = if (isPureBlack) Color(0xFF0A0A0C).copy(alpha = 0.85f) else Color(0xFF0D0B18).copy(alpha = 0.5f),
            borderColor = if (isPureBlack) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
            textColor = Color(0xFFF3F4F6),
            subTextColor = Color(0xFF94A3B8),
            accentColor = accent
        )
    } else {
        GlassCardColors(
            backgroundColor = Color(0xFFF5F3FF).copy(alpha = 0.55f),
            borderColor = Color(0xFFDDD6FE).copy(alpha = 0.5f),
            textColor = Color(0xFF2E1065),
            subTextColor = Color(0xFF6D28D9),
            accentColor = accent
        )
    }
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
    .background(
        Brush.verticalGradient(
            colors = listOf(
                backgroundColor,
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.45f).coerceAtLeast(0.01f))
            )
        )
    )
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = (borderColor.alpha * 0.25f).coerceAtLeast(0.01f))
            )
        ),
        shape = shape
    )

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
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "bounceScale"
    )

    return this
        .scale(scale)
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(bounded = true),
            enabled = enabled,
            onClick = onClick
        )
}

// 🌌 High-Performance GPU-Accelerated Ambient Background
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    customAccent: Color? = null,
    isPureBlack: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val darkTheme = LocalThemeIsDark.current
    val baseBgColor = if (darkTheme) {
        if (isPureBlack) Color(0xFF000000) else Color(0xFF05030C)
    } else {
        Color(0xFFF8F6FF)
    }

    val targetAccent = customAccent ?: (if (darkTheme) Color(0xFFC084FC) else Color(0xFF7C3AED))
    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "ambientAccent"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        if (!isPureBlack) {
            AmbientOrbs(
                accentColor = animatedAccent,
                darkTheme = darkTheme
            )
        }

        content()
    }
}

@Composable
private fun AmbientOrbs(
    accentColor: Color,
    darkTheme: Boolean
) {
    // Primary Top-Start Ambient Aura Glow (GPU RenderNode cached, zero continuous redraws)
    Box(
        modifier = Modifier
            .fillMaxWidth(1.25f)
            .aspectRatio(1f)
            .graphicsLayer {
                translationX = -90f
                translationY = -70f
                alpha = if (darkTheme) 0.36f else 0.22f
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accentColor,
                        accentColor.copy(alpha = 0f)
                    )
                ),
                shape = CircleShape
            )
    )

    // Secondary Bottom-End Ambient Mood Glow
    val secondaryColor = if (darkTheme) Color(0xFF38BDF8) else Color(0xFF818CF8)
    Box(
        modifier = Modifier
            .fillMaxWidth(1.05f)
            .aspectRatio(1f)
            .graphicsLayer {
                translationX = 130f
                translationY = 240f
                alpha = if (darkTheme) 0.20f else 0.12f
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        secondaryColor,
                        secondaryColor.copy(alpha = 0f)
                    )
                ),
                shape = CircleShape
            )
    )
}
