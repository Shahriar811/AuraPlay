package com.example.auraplay.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.auraplay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val AuraPlayFont = FontFamily(
    ManropeFont(FontWeight.Normal),
    ManropeFont(FontWeight.Medium),
    ManropeFont(FontWeight.SemiBold),
    ManropeFont(FontWeight.Bold),
    ManropeFont(FontWeight.ExtraBold)
)

private class ManropeFont(override val weight: FontWeight) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Async,
    typefaceLoader = ManropeTypefaceLoader
) {
    override val style: FontStyle = FontStyle.Normal
}

private object ManropeTypefaceLoader : AndroidFont.TypefaceLoader {
    private val loadMutex = Mutex()

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? = null

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = withContext(Dispatchers.IO) {
        // Async resolution can start undispatched during layout. Move the resource read itself off main.
        loadMutex.withLock {
            val base = ResourcesCompat.getFont(context, R.font.manrope) ?: error("Could not load Manrope")
            Paint().apply {
                typeface = base
                fontVariationSettings = "'wght' ${font.weight.weight}"
            }.typeface
        }
    }
}

// A shared family gives dialogs, controls, and song details the same typography.
// Android's system fallback supplies glyphs for scripts outside Manrope, including Bengali.
private val AppTextStyle = TextStyle(fontFamily = AuraPlayFont, letterSpacing = 0.sp)

val Typography = Typography(
    displayLarge = AppTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 56.sp),
    displayMedium = AppTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
    displaySmall = AppTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp),
    headlineLarge = AppTextStyle.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = AppTextStyle.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = AppTextStyle.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = AppTextStyle.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelSmall = AppTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
)
