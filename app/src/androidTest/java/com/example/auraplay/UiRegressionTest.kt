package com.example.auraplay

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.auraplay.data.AppDatabase
import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState
import com.example.auraplay.service.SleepTimerState
import com.example.auraplay.ui.*
import com.example.auraplay.ui.theme.AuraPlayTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UiRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: AppDatabase
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: MainViewModel

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuraPlayApplication
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            store = ViewModelStore()
            viewModel = ViewModelProvider(store, MainViewModelFactory(app, database.playlistDao(), app.settingsDataStore, database.featureDao()))[MainViewModel::class.java]
        }
    }

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        database.close()
    }

    @Test fun searchOpensVisibleAndFocusedAfterScrolling() {
        runBlocking {
            database.playlistDao().insertSongs((0..79).map { index ->
                Song(index.toLong(), "Track ${index.toString().padStart(3, '0')}", "Artist",
                    albumArtUri = null, duration = 60_000, data = "/Music/$index.mp3")
            })
        }
        compose.setContent {
            AuraPlayTheme(darkTheme = true) {
                HomeScreen(rememberNavController(), viewModel, {}, { _, _ -> })
            }
        }
        compose.waitUntil(10_000) { viewModel.songs.value.size == 80 }
        compose.onNodeWithTag("library-list").performScrollToIndex(65)
        compose.onNodeWithContentDescription("Search library").performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsFocused().performTextInput("Track 079")
        compose.waitUntil(10_000) { viewModel.songs.value.size == 1 }
        compose.onNodeWithText("Track 079").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    @Test fun multiSelectSurvivesSearchAndRecreationAndPlaysOnlyChosenSongs() {
        runBlocking {
            database.playlistDao().insertSongs((1L..5L).map {
                Song(it, "Track $it", "Artist", albumArtUri = null, duration = 60_000, data = "/Music/$it.mp3")
            })
        }
        viewModel.onSearchQueryChange("Track 1")
        var singlePlays = 0
        var played = emptyList<Song>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AuraPlayTheme(darkTheme = true) {
                HomeScreen(rememberNavController(), viewModel, { singlePlays++ }, { songs, _ -> played = songs })
            }
        }
        compose.waitUntil(10_000) { viewModel.songs.value.singleOrNull()?.id == 1L }
        compose.onNodeWithTag("song-1").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("Track 3")
        compose.waitUntil(10_000) { viewModel.songs.value.singleOrNull()?.id == 3L }
        compose.onNodeWithTag("song-3").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("Play selected (2)").performClick()
        compose.waitUntil(10_000) { played.size == 2 }
        compose.runOnIdle {
            assertEquals(listOf(1L, 3L), played.map { it.id })
            assertEquals(0, singlePlays)
        }
        compose.onNodeWithContentDescription("Cancel song selection").assertDoesNotExist()
    }

    @Test fun playerShortcutsAreVisibleAndInvokeTheirActionsDirectly() {
        var equalizer = 0
        var bookmarks = 0
        var timer = 0
        compose.setContent {
            AuraPlayTheme(darkTheme = false) {
                Box(Modifier.width(320.dp)) {
                    PlayerQuickActions(SleepTimerState(isActive = true, isEndOfTrack = true),
                        { equalizer++ }, { bookmarks++ }, { timer++ })
                }
            }
        }
        compose.onNodeWithText("Equalizer").assertIsDisplayed().performClick()
        compose.onNodeWithText("Bookmarks").assertIsDisplayed().performClick()
        compose.onNodeWithText("Sleep timer").assertIsDisplayed().assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(1, equalizer); assertEquals(1, bookmarks); assertEquals(1, timer) }
    }

    @Test fun queueHighlightTracksAccentAndMarksOnlyThePlayingOccurrence() {
        val track = Song(1, "Duplicate track", "Artist", albumArtUri = null, duration = 60_000, data = "")
        val dark = mutableStateOf(true)
        val accent = mutableStateOf(Color(0xFF38BDF8))
        var expected = Color.Unspecified
        compose.setContent {
            AuraPlayTheme(darkTheme = dark.value, accentColor = accent.value) {
                val highlight = MaterialTheme.colorScheme.primaryContainer
                SideEffect { expected = highlight }
                QueueBottomSheet(PlayerState(currentSong = track, queue = listOf(track, track),
                    queueKeys = listOf("first", "second"), queueOrder = listOf(0, 1), currentQueueIndex = 1),
                    {}, {}, {})
            }
        }
        for ((isDark, color) in listOf(true to Color(0xFF38BDF8), false to Color(0xFFFB923C))) {
            compose.runOnIdle { dark.value = isDark; accent.value = color }
            compose.waitForIdle()
            val defaultContainer = (if (isDark) darkColorScheme() else lightColorScheme()).primaryContainer
            assertNotEquals("A custom accent must replace Material's default purple container", defaultContainer.toArgb(), expected.toArgb())
            compose.onNodeWithTag("queue-row-first").assertIsNotSelected()
            val row = compose.onNodeWithTag("queue-row-second").assertIsSelected()
            val pixels = row.captureToImage().toPixelMap()
            assertEquals(expected.toArgb(), pixels[4, pixels.height / 2].toArgb())
        }
    }

    @Test fun playerOpensSleepTimerWithoutOpeningOverflowMenu() {
        val song = Song(999, "Timer track", "Artist", albumArtUri = null, duration = 180_000, data = "")
        compose.setContent {
            AuraPlayTheme(darkTheme = true) {
                Box(Modifier.size(340.dp, 600.dp)) {
                    PlayerScreen(rememberNavController(), viewModel, PlayerState(currentSong = song), {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Sleep timer").assertIsDisplayed().performClick()
        compose.onNodeWithText("Sleep Timer").assertIsDisplayed()
        compose.onNodeWithText("End of Current Song").assertIsDisplayed()
    }

    @Test fun miniPlayerSwipesChangeTracksOnceWithoutOpeningThePlayer() {
        var next = 0
        var previous = 0
        var opened = 0
        val song = Song(999, "Swipe track", "Artist", albumArtUri = null, duration = 180_000, data = "")
        compose.setContent {
            AuraPlayTheme(darkTheme = false) {
                Box(Modifier.width(340.dp)) {
                    MiniPlayer(song, false, {}, onNext = { next++ }, onClick = { opened++ },
                        modifier = Modifier.testTag("mini-player"), onPrevious = { previous++ })
                }
            }
        }
        compose.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, next); assertEquals(0, previous); assertEquals(0, opened) }
        compose.onNodeWithTag("mini-player").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, next); assertEquals(1, previous); assertEquals(0, opened) }
        // A swipe beginning over a button must cancel the button's click, too.
        compose.onNodeWithContentDescription("Next Track").performTouchInput {
            swipe(center, center - Offset(220.dp.toPx(), 0f))
        }
        compose.runOnIdle { assertEquals(2, next); assertEquals(1, previous); assertEquals(0, opened) }
    }

    @Test fun miniPlayerKeepsTapsAndPlaybackButtonsSeparateFromSwipes() {
        var toggled = 0
        var next = 0
        var previous = 0
        var opened = 0
        val song = Song(999, "Swipe track", "Artist", albumArtUri = null, duration = 180_000, data = "")
        compose.setContent {
            AuraPlayTheme(darkTheme = false) {
                Box(Modifier.width(340.dp)) {
                    MiniPlayer(song, false, { toggled++ }, onNext = { next++ }, onClick = { opened++ },
                        modifier = Modifier.testTag("mini-player"), onPrevious = { previous++ })
                }
            }
        }
        compose.onNodeWithText("Swipe track").performTouchInput { click() }
        compose.onNodeWithContentDescription("Play").performTouchInput { click() }
        compose.onNodeWithContentDescription("Next Track").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, opened); assertEquals(1, toggled); assertEquals(1, next); assertEquals(0, previous) }
        compose.onNodeWithTag("mini-player").performTouchInput {
            swipe(center, center + Offset(24.dp.toPx(), 0f))
        }
        compose.runOnIdle { assertEquals(1, next); assertEquals(0, previous); assertEquals(1, opened) }
    }

    @Test fun longTitlesAndLyricsKeepPlaybackControlsInPlace() {
        val short = Song(999, "Short title", "Artist", albumArtUri = null, duration = 180_000, data = "")
        val playback = mutableStateOf(PlayerState(currentSong = short, totalDuration = 180_000))
        compose.setContent {
            AuraPlayTheme(darkTheme = true) {
                Box(Modifier.size(340.dp, 600.dp)) {
                    PlayerScreen(rememberNavController(), viewModel, playback.value, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Previous track").assertIsDisplayed()
        val controls = playbackBounds()
        compose.runOnIdle { playback.value = playback.value.copy(currentSong = short.copy(title = "A very long song title ".repeat(12))) }
        compose.waitForIdle()
        assertEquals(controls, playbackBounds())
        compose.onNodeWithContentDescription("Toggle lyrics").performClick()
        compose.waitForIdle()
        assertEquals(controls, playbackBounds())
        compose.onNodeWithContentDescription("Toggle lyrics").performClick()
        assertEquals(controls, playbackBounds())
    }

    @Test fun rapidTrackChangesKeepControlsAnchoredAndSettleOnLatestSong() {
        val tracks = (1..3).map { index ->
            Song(990L + index, "Transition track $index", "Artist $index", albumArtUri = null,
                duration = 180_000, data = "")
        }
        val playback = mutableStateOf(PlayerState(currentSong = tracks[0], totalDuration = 180_000,
            queue = tracks, queueSize = 3, queueKeys = listOf("first", "second", "third"), queueOrder = listOf(0, 1, 2)))
        compose.setContent {
            AuraPlayTheme(darkTheme = true) {
                Box(Modifier.size(340.dp, 600.dp)) {
                    PlayerScreen(rememberNavController(), viewModel, playback.value, {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.waitForIdle()
        val controls = playbackBounds()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { playback.value = playback.value.copy(currentSong = tracks[1], currentQueueIndex = 1) }
        compose.mainClock.advanceTimeBy(96)
        assertEquals(controls, playbackBounds())
        compose.runOnUiThread { playback.value = playback.value.copy(currentSong = tracks[2], currentQueueIndex = 2) }
        compose.mainClock.advanceTimeBy(96)
        assertEquals(controls, playbackBounds())
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.onNodeWithText("Transition track 3").assertIsDisplayed()
        compose.onNodeWithText("Transition track 1").assertDoesNotExist()
        compose.onNodeWithText("Transition track 2").assertDoesNotExist()
        assertEquals(controls, playbackBounds())
        compose.mainClock.autoAdvance = true
    }

    private fun playbackBounds(): List<Rect> = listOf(
        compose.onNodeWithTag("player-controls").fetchSemanticsNode().boundsInRoot,
        compose.onNodeWithTag("player-progress").fetchSemanticsNode().boundsInRoot,
        compose.onNodeWithContentDescription("Previous track").fetchSemanticsNode().boundsInRoot,
        compose.onNodeWithContentDescription("Next track").fetchSemanticsNode().boundsInRoot,
        compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot
    )

    @Test fun selectingTabsAlwaysOpensTheirRootsFromNestedScreens() {
        lateinit var nav: NavHostController
        compose.setContent {
            AuraPlayTheme(darkTheme = true) {
                nav = rememberNavController()
                Column {
                    Row {
                        listOf("home", "stats", "playlists").forEach { route ->
                            Button(onClick = { nav.openTopLevel(route) }) { Text("Open $route") }
                        }
                    }
                    NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                        listOf("home", "stats", "playlists", "settings", "equalizer", "playlist_details").forEach { route ->
                            composable(route) { Text("Screen $route") }
                        }
                    }
                }
            }
        }
        for (target in listOf("home", "stats", "playlists", "home", "playlists", "stats")) {
            compose.runOnIdle { nav.navigate("settings"); nav.navigate("equalizer") }
            compose.onNodeWithText("Screen equalizer").assertIsDisplayed()
            compose.onNodeWithText("Open $target").performClick()
            compose.onNodeWithText("Screen $target").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(target, nav.currentDestination?.route)
                if (target == "home") assertNull(nav.previousBackStackEntry)
                else assertEquals("home", nav.previousBackStackEntry?.destination?.route)
            }
        }
    }
}
