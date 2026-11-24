package com.example.auraplay

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auraplay.ui.*
import com.example.auraplay.ui.theme.AuraPlayTheme
import com.example.auraplay.data.Playlist
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class PopupStyleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playlistMenuOpensBesideItsOverflowButton() {
        compose.setContent {
            AuraPlayTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().padding(top = 72.dp)) {
                    PlaylistItem(Playlist(name = "Workout"), {}, {}, {})
                }
            }
        }

        val overflow = compose.onNodeWithContentDescription("More options")
        overflow.performClick()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithText("Delete playlist").assertIsDisplayed()
        val anchor = overflow.fetchSemanticsNode()
        val menu = compose.onNodeWithTag("aura-popup-menu").fetchSemanticsNode()
        compose.runOnIdle {
            val anchorPosition = anchor.positionOnScreen
            val menuPosition = menu.positionOnScreen
            val tolerance = with(compose.density) { 2.dp.toPx() }
            val horizontalGap = menuPosition.x + menu.size.width - (anchorPosition.x + anchor.size.width)
            val verticalGap = menuPosition.y - (anchorPosition.y + anchor.size.height)
            assertTrue("The menu should align with the overflow button's right edge", abs(horizontalGap) <= tolerance)
            assertTrue("The menu should open directly below the overflow button", abs(verticalGap) <= tolerance)
        }
    }

    @Test fun openMenuFollowsLightAndDarkThemesWithLargeText() {
        val dark = mutableStateOf(false)
        val fontScale = mutableStateOf(1.5f)
        var expected = Color.Unspecified
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                AuraPlayTheme(darkTheme = dark.value, accentColor = Color(0xFFFBBF24)) {
                    expected = MaterialTheme.colorScheme.surfaceContainerLow
                    Box(Modifier.padding(24.dp).size(48.dp)) {
                        AuraDropdownMenu(true, {}) {
                            val popupDensity = LocalDensity.current
                            // Popup owns a separate window and density. Apply the test font scale there.
                            CompositionLocalProvider(LocalDensity provides Density(popupDensity.density, fontScale.value)) {
                                AuraMenuItem("Play next", {}, Icons.Rounded.SkipNext, enabled = false)
                                AuraMenuItem("Remove from liked songs", {}, Icons.Rounded.FavoriteBorder, selected = true)
                                AuraMenuDivider()
                                AuraMenuItem("Delete from device", {}, Icons.Rounded.DeleteOutline, destructive = true)
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Play next").assertIsNotEnabled()
        compose.onNodeWithText("Remove from liked songs").assertIsDisplayed().assertIsSelected()
        val largeRowHeight = compose.onNodeWithText("Remove from liked songs").fetchSemanticsNode().boundsInRoot.height
        verifySurface("aura-popup-menu", { expected }, "menu-light-large-text")
        compose.runOnIdle { dark.value = true }
        compose.onNodeWithText("Delete from device").assertIsDisplayed()
        verifySurface("aura-popup-menu", { expected }, "menu-dark-large-text")
        compose.runOnIdle { dark.value = false; fontScale.value = 1f }
        verifySurface("aura-popup-menu", { expected }, "menu-light")
        val normalRowHeight = compose.onNodeWithText("Remove from liked songs").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Large text must be measured without clipping the action", largeRowHeight > normalRowHeight)
    }

    @Test fun dialogFollowsThemeWithoutFallingBackToPurple() {
        val dark = mutableStateOf(false)
        var expected = Color.Unspecified
        compose.setContent {
            AuraPlayTheme(darkTheme = dark.value, accentColor = Color(0xFF38BDF8)) {
                expected = MaterialTheme.colorScheme.surfaceContainerLow
                AuraAlertDialog(onDismissRequest = {}, title = { Text("Audio info") },
                    text = { Text("Song details use the same surface and typography as the music library.") },
                    confirmButton = { TextButton(onClick = {}) { Text("Done") } })
            }
        }
        compose.onNodeWithText("Done").assertIsDisplayed()
        verifySurface("aura-dialog", { expected }, "dialog-light")
        compose.runOnIdle { dark.value = true }
        verifySurface("aura-dialog", { expected }, "dialog-dark")
    }

    private fun verifySurface(tag: String, expectedColor: () -> Color, name: String) {
        compose.waitForIdle()
        val expected = expectedColor()
        val capture = compose.onNodeWithTag(tag).captureToImage()
        val pixels = capture.toPixelMap()
        val actual = pixels[pixels.width / 2, 4]
        assertTrue("Popup surface must follow the active theme. Expected $expected, got $actual",
            abs(actual.red - expected.red) < 0.035f &&
                abs(actual.green - expected.green) < 0.035f && abs(actual.blue - expected.blue) < 0.035f)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.externalCacheDir, "popup-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            capture.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
