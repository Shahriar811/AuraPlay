# Playback and interaction motion

The motion takes inspiration from Spotify: short, eased song transitions, tactile presses, and clear changes between playback states.

- Artwork and track details slide and fade over 340 ms in both players. Direction follows playback order, including shuffle and queue wrapping. Queue occurrence keys allow duplicate tracks to transition independently.
- The artwork follows a horizontal swipe and springs back when the gesture ends. Lyrics fade and slide within the existing viewport; the playback controls stay anchored.
- Play/pause, favorites, and repeat icons fade and scale between states. Shuffle, repeat, lyrics, and the selected song use animated colors. Artwork accent changes blend over 360 ms.
- Shared Material button wrappers add a short press and spring release to buttons, icon buttons, chips, and floating actions throughout the app. Navigation items use the same feedback. Quick taps still show the press; actions run immediately, and cancelled or disabled presses return to rest.
- Opening the player slides it up; closing it slides it down. Other screen changes use a short fade.
- Motion uses Compose animation APIs, which observe Android's animator duration scale. List thumbnails remain bounded and cached, with no per-image loading crossfade. Playback progress does not restart song transitions.

The implementation follows Android's [Compose animation guidance](https://developer.android.com/develop/ui/compose/animation/quick-guide) and [interaction guidance](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions), including visible feedback for presses that begin and end within one frame.

## Validation

```powershell
$env:JAVA_HOME = 'C:\Users\mdsha\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assemblePerformance :app:lintDebug --offline --console=plain --max-workers=2
```

Debug, Android test, and optimized performance APKs built successfully. All 35 JVM tests passed. Lint completed with zero errors and 74 warnings. The optimized APK is at `app/build/outputs/apk/performance/app-performance.apk`.

The UI regression suite now also samples control positions during interrupted song transitions and checks that rapid skipping settles on the latest title. Android UI tests require a connected device; none was available during this update. On a device, check next/previous, shuffled queues, rapid skipping, play/pause, liking a song, repeat modes, lyrics, short taps and cancelled presses, and Android's disabled-animation setting.
