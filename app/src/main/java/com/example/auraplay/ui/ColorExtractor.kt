package com.example.auraplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractedColors(
    val dominantColor: Color = Color(0xFFC084FC),
    val vibrantColor: Color = Color(0xFF9333EA),
    val darkMutedColor: Color = Color(0xFF1E1035),
    val lightMutedColor: Color = Color(0xFFE9D5FF)
)

object ColorExtractor {

    private val DEFAULT_VIVID_FALLBACK = Color(0xFFC084FC) // Radiant Electric Purple

    suspend fun extractColors(context: Context, uriString: String?): ExtractedColors = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank() || uriString == "null") {
            return@withContext ExtractedColors()
        }

        try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                val palette = Palette.from(bitmap)
                    .maximumColorCount(24)
                    .generate()

                val vividColor = selectBestVividColor(palette)

                return@withContext ExtractedColors(
                    dominantColor = vividColor,
                    vibrantColor = vividColor,
                    darkMutedColor = Color(0xFF1E1035),
                    lightMutedColor = Color(0xFFE9D5FF)
                )
            }
        } catch (e: Exception) {
            // Fallback
        }

        ExtractedColors()
    }

    /**
     * Filters out gray, white, pale, and washed-out colors.
     * Selects and boosts only rich, colorful, and vivid hues for Dynamic Aura.
     */
    fun selectBestVividColor(palette: Palette): Color {
        // 1. Check vibrant swatches first
        val vibrantCandidates = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch
        )

        for (swatch in vibrantCandidates) {
            if (isSwatchVivid(swatch)) {
                return boostToVividColor(swatch.rgb)
            }
        }

        // 2. Score and sort all extracted palette swatches by color vibrance & saturation
        val scoredSwatches = palette.swatches
            .map { swatch -> swatch to calculateVividnessScore(swatch) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }

        if (scoredSwatches.isNotEmpty()) {
            return boostToVividColor(scoredSwatches.first().first.rgb)
        }

        // 3. Check dominant and muted swatches; if they have some color hue, boost into vivid color
        val fallbackCandidates = listOfNotNull(
            palette.dominantSwatch,
            palette.mutedSwatch,
            palette.darkMutedSwatch,
            palette.lightMutedSwatch
        )

        for (swatch in fallbackCandidates) {
            val hsl = swatch.hsl
            if (hsl[1] >= 0.20f && hsl[2] in 0.15f..0.85f) {
                return boostToVividColor(swatch.rgb)
            }
        }

        // 4. Monochrome/grayscale album art fallback (never return gray or white)
        return DEFAULT_VIVID_FALLBACK
    }

    private fun isSwatchVivid(swatch: Palette.Swatch): Boolean {
        val hsl = swatch.hsl
        val saturation = hsl[1]
        val lightness = hsl[2]

        // Reject gray / low saturation colors (less than 35% color saturation)
        if (saturation < 0.35f) return false

        // Reject washed-out white / near-white colors
        if (lightness > 0.78f) return false
        if (lightness > 0.68f && saturation < 0.50f) return false

        // Reject near-black colors (less than 18% lightness)
        if (lightness < 0.18f) return false

        return true
    }

    private fun calculateVividnessScore(swatch: Palette.Swatch): Float {
        val hsl = swatch.hsl
        val saturation = hsl[1]
        val lightness = hsl[2]

        if (!isSwatchVivid(swatch)) return -1f

        // Favor rich mid-lightness (0.45..0.65) and strong saturation
        val lightnessScore = 1f - kotlin.math.abs(lightness - 0.55f) * 2f
        val saturationScore = saturation

        return (saturationScore * 0.65f) + (lightnessScore * 0.35f)
    }

    fun boostToVividColor(rgb: Int): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)

        // Boost saturation for rich colorful vibrancy
        hsl[1] = hsl[1].coerceIn(0.70f, 0.98f)
        // Normalize lightness so the color is always visible and striking in both Dark & Light UI
        hsl[2] = hsl[2].coerceIn(0.48f, 0.62f)

        val boostedRgb = ColorUtils.HSLToColor(hsl)
        return Color(boostedRgb)
    }
}

@Composable
fun rememberDominantColors(context: Context, albumArtUri: String?): ExtractedColors {
    var colors by remember(albumArtUri) { mutableStateOf(ExtractedColors()) }

    LaunchedEffect(albumArtUri) {
        colors = ColorExtractor.extractColors(context, albumArtUri)
    }

    return colors
}
