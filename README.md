# AuraPlay Music Player

AuraPlay is a lightweight, modern music player application for Android, built entirely with Kotlin and Jetpack Compose. It's designed to be a clean, elegant, and simple solution for playing local audio files.

## ✨ Features

* **Modern UI:** Built with Material 3 and Jetpack Compose for a clean, dynamic, and responsive user interface.
* **Light & Dark Theme:** Includes a premium light and dark theme, with the option to toggle saved to DataStore.
* **Local Music Library:** Automatically scans and loads all audio files from your device's MediaStore.
* **Playback Control:** Full playback functionality using Media3 (ExoPlayer), running in a foreground service (`MediaSessionService`) for background playback.
* **Playlist Management:**
    * Create, rename, and delete custom playlists.
    * Add songs to playlists from the main library.
    * View and manage songs within each playlist.
* **Favorites:** Mark any song as a "favorite" and access all favorite songs in a dedicated screen.
* **Search & Sort:**
    * Search your library by song title or artist.
    * Sort your music by Title, Artist, or Date Added.
* **Database:** Uses Room to locally persist all song data, playlists, and favorite statuses.
* **Permission Handling:** Gracefully requests the necessary permissions to read audio files (handles new Android 13+ `READ_MEDIA_AUDIO` permission).

## 🛠 Tech Stack

* **Language:** 100% [Kotlin](https://kotlinlang.org/)
* **UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
* **Architecture:** MVVM (ViewModel)
* **Audio Playback:** [Media3 (ExoPlayer & MediaSession)](https://developer.android.com/guide/topics/media/media3)
* **Database:** [Room](https://developer.android.com/jetpack/androidx/room) for local persistence
* **Asynchronous:** [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://developer.android.com/kotlin/flow)
* **Preferences:** [DataStore](https://developer.android.com/topic/libraries/datastore) for saving user settings (e.g., theme)
* **Image Loading:** [Coil](https://coil-kt.github.io/coil/) for loading album art
* **Navigation:** [Jetpack Navigation Compose](https://developer.android.com/jetpack/compose/navigation)

## 🚀 How to Build

This is a standard Android Studio project.

1.  **Clone** the repository:
    ```bash
    git clone [https://github.com/your-username/AuraPlay.git](https://github.com/your-username/AuraPlay.git)
    ```
2.  **Open** the project in Android Studio.
3.  **Build** and **Run** the app on an Android emulator or a physical device.

The app will require permission to access audio files on the device to function correctly.
