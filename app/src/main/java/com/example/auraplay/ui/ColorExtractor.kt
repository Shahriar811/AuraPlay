package com.example.auraplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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
                val palette = Palette.from(bitmap).generate()
                val dominant = palette.getDominantColor(0xFFC084FC.toInt())
                val vibrant = palette.getVibrantColor(palette.getDominantColor(0xFF9333EA.toInt()))
                val darkMuted = palette.getDarkMutedColor(0xFF1E1035.toInt())
                val lightMuted = palette.getLightMutedColor(0xFFE9D5FF.toInt())

                return@withContext ExtractedColors(
                    dominantColor = Color(dominant),
                    vibrantColor = Color(vibrant),
                    darkMutedColor = Color(darkMuted),
                    lightMutedColor = Color(lightMuted)
                )
            }
        } catch (e: Exception) {
            // Log or fallback
        }

        ExtractedColors()
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
