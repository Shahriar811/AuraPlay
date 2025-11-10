package com.example.auraplay.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.auraplay.MainViewModel
import com.example.auraplay.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    navController: NavController,
    viewModel: MainViewModel,
    onSongSelected: (Song) -> Unit
) {
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Favorite Songs") })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(favoriteSongs) { song ->
                SongListItem(
                    song = song,
                    onSongSelected = { onSongSelected(song) },
                    trailingContent = {
                        // Add a toggle here to quickly remove from favorites
                        IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Remove from Favorites",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}