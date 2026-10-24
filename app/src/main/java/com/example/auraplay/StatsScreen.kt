package com.example.auraplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.auraplay.MainViewModel
import com.example.auraplay.data.Song
import com.example.auraplay.features.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(nav: NavController, viewModel: MainViewModel, onPlay: (List<Song>, Song) -> Unit) {
    val library by viewModel.visibleLibrary.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val events by viewModel.listeningEvents.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(java.time.LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); today = java.time.LocalDate.now() } }
    val summary by produceState(summarizeListening(emptyList(), System.currentTimeMillis()), events, today) {
        value = withContext(Dispatchers.Default) { summarizeListening(events, System.currentTimeMillis()) }
    }
    val colors = glassCardColors()
    val month = remember(events, today) {
        val start = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        events.filter { it.startedAt >= start && it.durationMs > 0 }
    }
    Scaffold(containerColor = Color.Transparent,
        topBar = { TopAppBar(title = { Text("Your listening", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(listeningTime(summary.todayMs), style = MaterialTheme.typography.headlineLarge, color = colors.accentColor, fontWeight = FontWeight.Bold)
                    Text("Listened today", color = colors.subTextColor)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(16.dp)) { Text(listeningTime(summary.weekMs), fontWeight = FontWeight.Bold); Text("Last 7 days", style = MaterialTheme.typography.bodySmall) } }
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(16.dp)) { Text("${library.sumOf { it.playCount.toLong() }}", fontWeight = FontWeight.Bold); Text("Total plays", style = MaterialTheme.typography.bodySmall) } }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Your week", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val maximum = summary.daily.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1L
                    Row(Modifier.fillMaxWidth().height(166.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        summary.daily.forEach { (day, duration) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Text("${duration / 60000}m", style = MaterialTheme.typography.labelSmall, maxLines = 1, color = colors.subTextColor)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.fillMaxWidth().height((duration.toFloat() / maximum * 100f).coerceAtLeast(3f).dp).background(colors.accentColor, MaterialTheme.shapes.small))
                                Spacer(Modifier.height(6.dp))
                                Text(day.format(DateTimeFormatter.ofPattern("EEEEE")), style = MaterialTheme.typography.labelSmall, color = colors.subTextColor)
                            }
                        }
                    }
                }
            }
            item { Text("Artists this week", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (summary.artists.isEmpty()) item { Text("Artist rankings appear as you listen.", Modifier.padding(horizontal = 20.dp), color = colors.subTextColor) }
            items(summary.artists, key = { it.first }) { (artist, duration) ->
                ListItem(headlineContent = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = {
                    LinearProgressIndicator(progress = { (duration.toFloat() / summary.artists.first().second.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }, trailingContent = { Text(listeningTime(duration), color = colors.accentColor) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${listeningTime(summary.monthMs)} listened")
                    Text("${month.map { it.songId }.distinct().size} songs · ${month.map { it.artist }.distinct().size} artists")
                    Text("Time counts actual playback, including background listening. Detailed history starts with this update.", style = MaterialTheme.typography.bodySmall, color = colors.subTextColor)
                } }
            }
            item { Text("On repeat", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (mostPlayed.isEmpty()) item { Text("Play a song for 10 seconds to start your play counts.", Modifier.padding(horizontal = 20.dp), color = colors.subTextColor) }
            items(mostPlayed, key = { it.id }) { song ->
                SongListItem(song, onSongSelected = { onPlay(mostPlayed, song) }, trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("${song.playCount}", color = colors.accentColor); FeatureSongMenu(song, nav, viewModel) }
                })
            }
        }
    }
}

private fun listeningTime(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60000
    return when { ms in 1..59999 -> "<1 min"; minutes < 60 -> "$minutes min"; else -> "${minutes / 60}h ${minutes % 60}m" }
}
