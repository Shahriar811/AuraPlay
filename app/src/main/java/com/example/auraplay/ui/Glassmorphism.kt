package com.example.auraplay.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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

// 🪄 Glassmorphic Modifier with Modern Soft Drop Shadows
fun Modifier.glassmorphic(
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    backgroundColor: Color = Color.White.copy(alpha = 0.06f),
    shadowElevation: Dp = 6.dp
): Modifier = this
    .shadow(
        elevation = shadowElevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.05f),
        spotColor = Color.Black.copy(alpha = 0.1f)
    )
    .graphicsLayer {
        this.shape = shape
        this.clip = true
    }
    .background(
        Brush.verticalGradient(
            colors = listOf(
                backgroundColor,
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.4f).coerceAtLeast(0.01f))
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

// 🌌 Ambient Background with GPU-Accelerated Animated Glow Orbs
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    customAccent: Color? = null,
    isPureBlack: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    
    val orb1XState = infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb1X"
    )
    val orb1YState = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(19000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb1Y"
    )
    
    val orb2XState = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb2X"
    )
    val orb2YState = infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb2Y"
    )

    val orb3XState = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb3X"
    )
    val orb3YState = infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(21000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "orb3Y"
    )
    
    val darkTheme = LocalThemeIsDark.current
    val baseBgColor = if (darkTheme) {
        if (isPureBlack) Color(0xFF000000) else Color(0xFF03000A)
    } else {
        Color(0xFFF5F3FF)
    }
    
    val accentOrbColor = customAccent ?: (if (darkTheme) Color(0xFFC084FC) else Color(0xFF7C3AED))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
            .drawBehind {
                val w = size.width
                val h = size.height
                
                val o1X = orb1XState.value
                val o1Y = orb1YState.value
                val o2X = orb2XState.value
                val o2Y = orb2YState.value
                val o3X = orb3XState.value
                val o3Y = orb3YState.value
                
                if (darkTheme && !isPureBlack) {
                    // Orb 1: Primary Accent Tint
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentOrbColor.copy(alpha = 0.22f),
                                accentOrbColor.copy(alpha = 0f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(w * o1X, h * o1Y),
                            radius = w * 0.7f
                        ),
                        center = androidx.compose.ui.geometry.Offset(w * o1X, h * o1Y),
                        radius = w * 0.7f
                    )
                    
                    // Orb 2: Radiant Mint Green / Cyan
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF34D399).copy(alpha = 0.16f),
                                Color(0xFF34D399).copy(alpha = 0f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(w * o2X, h * o2Y),
                            radius = w * 0.6f
                        ),
                        center = androidx.compose.ui.geometry.Offset(w * o2X, h * o2Y),
                        radius = w * 0.6f
                    )

                    // Orb 3: Cyber Pink
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFF472B6).copy(alpha = 0.14f),
                                Color(0xFFF472B6).copy(alpha = 0f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(w * o3X, h * o3Y),
                            radius = w * 0.5f
                        ),
                        center = androidx.compose.ui.geometry.Offset(w * o3X, h * o3Y),
                        radius = w * 0.5f
                    )
                } else if (!darkTheme) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentOrbColor.copy(alpha = 0.18f),
                                accentOrbColor.copy(alpha = 0f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(w * o1X, h * o1Y),
                            radius = w * 0.75f
                        ),
                        center = androidx.compose.ui.geometry.Offset(w * o1X, h * o1Y),
                        radius = w * 0.75f
                    )
                    
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFECFDF5).copy(alpha = 0.6f),
                                Color(0xFFECFDF5).copy(alpha = 0f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(w * o2X, h * o2Y),
                            radius = w * 0.65f
                        ),
                        center = androidx.compose.ui.geometry.Offset(w * o2X, h * o2Y),
                        radius = w * 0.65f
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF1F2).copy(alpha = 0.7f),
                                Color(0xFFFFF1F2).copy(alpha = 0f)
                            ),
                            center = androidx.compose.ui.geometry.Offset(w * o3X, h * o3Y),
                            radius = w * 0.6f
                        ),
                        center = androidx.compose.ui.geometry.Offset(w * o3X, h * o3Y),
                        radius = w * 0.6f
                    )
                }
            }
    ) {
        content()
    }
}
