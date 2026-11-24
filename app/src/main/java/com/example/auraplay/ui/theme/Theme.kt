package com.example.auraplay.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AuraPlayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

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
    error = Color(0xFFBA1A1A),
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

    val themedColors = remember(colorScheme, darkTheme, accentColor, pureBlack) {
        // A frame-by-frame change here invalidates every color consumer, including scrolling rows.
        val primary = accentColor?.let { if (darkTheme) it else lerp(it, Color(0xFF102012), 0.55f) } ?: colorScheme.primary
        colorScheme.copy(
            primary = primary,
            primaryContainer = lerp(colorScheme.surface, primary, if (darkTheme) 0.20f else 0.16f),
            onPrimaryContainer = colorScheme.onSurface,
            // Material popups use these roles rather than surface. Set every level so
            // menus, dialogs and sheets cannot fall back to Material's purple palette.
            surfaceTint = primary,
            surfaceContainerLowest = colorScheme.background,
            surfaceContainerLow = lerp(colorScheme.surface, primary, if (darkTheme) 0.03f else 0.015f),
            surfaceContainer = lerp(colorScheme.surface, primary, if (darkTheme) 0.05f else 0.03f),
            surfaceContainerHigh = lerp(colorScheme.surface, primary, if (darkTheme) 0.08f else 0.05f),
            surfaceContainerHighest = lerp(colorScheme.surface, primary, if (darkTheme) 0.12f else 0.08f),
            surfaceBright = if (darkTheme) lerp(colorScheme.surface, colorScheme.onSurface, 0.10f) else colorScheme.surface,
            surfaceDim = if (darkTheme) colorScheme.background else lerp(colorScheme.surface, colorScheme.onSurface, 0.08f),
            inverseSurface = if (darkTheme) LightSurface else DarkSurface,
            inverseOnSurface = if (darkTheme) LightOnSurface else DarkOnSurface,
            inversePrimary = if (darkTheme) lerp(primary, Color(0xFF102012), 0.55f) else accentColor ?: DarkPrimary,
            background = if (darkTheme && pureBlack) Color.Black else colorScheme.background
        )
    }
    CompositionLocalProvider(
        LocalThemeIsDark provides darkTheme,
        LocalContentColor provides themedColors.onBackground
    ) {
        MaterialTheme(
            colorScheme = themedColors,
            typography = Typography,
            shapes = AuraPlayShapes,
            content = content
        )
    }
}
