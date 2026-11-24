package com.example.auraplay.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTextApi::class)
@RunWith(AndroidJUnit4::class)
class FontLoadingTest {
    @Test
    fun preloadedFamilyUsesBundledFontForEveryWeight() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = createFontFamilyResolver(context)
        withTimeout(10_000) { resolver.preload(AuraPlayFont) }
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).forEach { weight ->
            val bundled = resolver.resolve(AuraPlayFont, weight, FontStyle.Normal, FontSynthesis.None).value
            val system = resolver.resolve(FontFamily.Default, weight, FontStyle.Normal, FontSynthesis.None).value
            assertNotEquals("Bundled font must not remain on the system fallback at $weight", system, bundled)
        }
        val regular = resolver.resolve(AuraPlayFont, FontWeight.Normal, FontStyle.Normal, FontSynthesis.None).value as Typeface
        val extraBold = resolver.resolve(AuraPlayFont, FontWeight.ExtraBold, FontStyle.Normal, FontSynthesis.None).value as Typeface
        val regularInk = render(regular)
        val boldInk = render(extraBold)
        try {
            assertFalse("Variable weight must change the rendered glyphs", regularInk.sameAs(boldInk))
        } finally {
            regularInk.recycle()
            boldInk.recycle()
        }
    }

    private fun render(face: Typeface): Bitmap = Bitmap.createBitmap(512, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = 56f
            color = android.graphics.Color.BLACK
        }
        Canvas(bitmap).drawText("AuraPlay Music", 8f, 68f, paint)
    }
}
