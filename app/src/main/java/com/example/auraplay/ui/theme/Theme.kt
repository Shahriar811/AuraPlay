package com.example.auraplay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

// 🎨 CompositionLocal to share the app-level dark theme state reactively
val LocalThemeIsDark = staticCompositionLocalOf { false }

// Reference-inspired dark palette.
private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB6BDB0),
    secondaryContainer = Color(0xFF303D2C),
    onSecondaryContainer = DarkPrimary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkAccentRuby,
    onError = Color.White
)

// Matching light palette.
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    tertiary = LightTertiary,
    onTertiary = Color.Black,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF53604F),
    secondaryContainer = Color(0xFFDCE8D5),
    onSecondaryContainer = LightOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightAccentRuby,
    onError = Color.White
)

// 🌗 AuraPlay Theme Wrapper
@Composable
fun AuraPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Enforce brand colors by default
    accentColor: Color? = null,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val animatedAccent by animateColorAsState(
        targetValue = accentColor?.let { if (darkTheme) it else lerp(it, Color(0xFF102012), 0.55f) } ?: colorScheme.primary,
        animationSpec = tween(360), label = "artwork-accent"
    )
    val themedColors = colorScheme.copy(
        primary = animatedAccent,
        primaryContainer = lerp(colorScheme.surface, animatedAccent, if (darkTheme) 0.20f else 0.16f),
        onPrimaryContainer = colorScheme.onSurface,
        background = if (darkTheme && pureBlack) Color.Black else colorScheme.background
    )
    CompositionLocalProvider(
        LocalThemeIsDark provides darkTheme,
        LocalContentColor provides themedColors.onBackground
    ) {
        MaterialTheme(
            colorScheme = themedColors,
            typography = Typography,
            content = content
        )
    }
}
