# Launch scrolling and player controls — October 24, 2026

## Findings and changes

The previous scrolling fixes remain in place. This pass found additional work introduced around saved playback and the mini-player:

- The mini-player rebuilt its entire playback-order list on every 250 ms position tick, even though the resulting UI state was unchanged. It now filters progress-only updates before traversing the queue. A regression test sends 100 ticks with a 10,000-item queue and verifies exactly one traversal. Track changes, duplicate queue occurrences, shuffle changes, play/pause and metadata edits still update the mini-player.
- Saved-queue occurrence keys and ordering were constructed on the main thread after the background restore finished. The complete preview is now prepared on a background dispatcher. Unchanged saved snapshots are filtered before restoration, so unrelated preference writes do not rebuild them.
- Every cold process launch scanned all MediaStore audio and compared every cached song, even if storage had not changed. Android 11+ now checks the version and generation of every mounted external volume and reuses the existing Room library when both match the successful scan. A storage change, removed/added volume, empty cache, unsupported provider or older Android version takes the full-scan path. Explicit Refresh always scans. The marker is saved only when the versions/generations are identical before and after scanning, and is excluded from portable backups. See Android's [MediaStore change-detection guidance](https://developer.android.com/training/data-storage/shared/media).
- Library overflow buttons now use the standard Material ripple, without a separate press-animation coroutine and graphics layer for each row. Closed overflow menus are not composed. Daily-mix artwork has an explicit decode-size bound.
- `app/src/main/baseline-prof.txt` adds app startup and library rendering rules to the profiles already provided by dependencies. These are manually maintained rules, not a device-recorded profile. The optimized build expands and rewrites them through R8. Their benefit depends on ART installing/compiling the profile; merely packaging a profile is not a measured smoothness result. See Android's [manual Baseline Profile guidance](https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure).

The first scan on a new installation still needs to discover the device's music. The work runs on the I/O dispatcher. This change does not pretend a new library can be displayed before that scan completes.

## Queue theme

The current queue row used `primaryContainer`, but the custom theme only replaced `primary`. Material's default purple container therefore remained visible. `primaryContainer` and its foreground now derive from the selected theme; the row title uses the selected accent. Inactive queue rows and the sheet use the app's surface. Current-song selection continues to use occurrence keys, so duplicate copies of a song are not all highlighted.

## Multiple-song playback

In Home → Songs, long-press a song or tap **Select songs** beside the library sorting control. Tap rows to select/deselect, use **Select all** for the current search results, then **Play selected** to start a queue containing just those songs. Selection order determines queue order; existing shuffle settings still apply to playback.

Selections survive searches and saved screen-state recreation. Selecting/deselecting all search results keeps selections from other searches. Back or the close button exits selection mode. Songs removed or excluded from the visible library before playback are omitted. Normal single-tap playback and song overflow actions remain available outside selection mode.

## Player shortcuts

**Equalizer**, **Bookmarks** and **Sleep timer** have labeled buttons on the playback screen. The timer button shows its active state and opens the existing timer dialog. Bookmark creation/listing and the equalizer use their existing implementations. The overflow menu retains song editing and playback speed.

Artwork and controls remain in separate areas on regular portrait screens. Short portrait screens and larger font settings can scroll the player content; landscape keeps an independently scrollable control area so the added actions remain reachable.

## Validation

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assemblePerformance :app:lintDebug --console=plain --max-workers=2
```

The five new JVM tests cover repeated large-queue progress ticks, playback/queue changes, safe scan-cache invalidation, selection across search results, selection order and removed songs. Compose regression tests cover saved selection/search/playback, direct player shortcuts, timer opening, and actual queue highlight pixels under light/dark accents with duplicate songs.

Validation result: all 51 JVM tests passed with no failures or errors; Android test sources compiled and the test APK built successfully. The optimized build succeeded, and debug lint reports zero errors and 87 warnings (dependency, resource, existing style and API notices).

No Android device or emulator is connected. Device UI tests can be compiled here but need a device to execute. First-launch frame timing has not been measured. Before calling performance verified, install the optimized APK and compare cold-launch scrolling with later scrolling on the same phone and music library, including fresh installation, unchanged-library relaunch, newly added music and removable storage changes. Also check small screens, large text and playing/paused mini-player states.

Installable optimized output: `app/build/outputs/apk/performance/app-performance.apk` (version 1.1.2 / code 4). Use the performance variant for end-user scrolling checks; the debug variant is for development.

The final APK is non-debuggable, includes `assets/dexopt/baseline.prof` and `baseline.profm`, and passes APK v2 signature verification with the same signing certificate as the previous local APK. Size: 3,233,013 bytes. SHA-256: `46b0723585c7f1ae28b13cbbbcdfd552a242e716d08454cb87194550be5be377`.

## Pixel 7 Pro scrolling measurement — November 24, 2026

The connected Pixel 7 Pro had an Android Studio deployment with `DEBUGGABLE`, `TEST_ONLY`, deployment overlays and ART status `run-from-apk`. A cold launch followed by six swipes rendered 69 frames, with 55 janky frames (79.71%). The median frame took 44 ms and the 90th percentile took 129 ms. Later scrolling rendered 180 frames, with 61 janky frames (33.89%) and a 19 ms median. The system trace showed main-thread dex verification and compilation of Compose scrolling code during interaction. The longest main-thread frames exceeded 400 ms. After installing the optimized APK, its longest recorded main-thread frame was 78 ms.

Installing `app/build/outputs/apk/performance/app-performance.apk` with `adb install -r` retained the existing library and settings. The installed APK was non-debuggable, with ART status `verify`; no forced compilation was applied. Using the same trace and six-swipe sequence, cold scrolling rendered 313 frames, with six janky frames (1.92%), a 6 ms median, a 9 ms 90th percentile and an 11 ms 95th percentile. Later scrolling rendered 325 frames with zero janky frames, a 6 ms median and a 9 ms 90th percentile. These measurements describe this phone and its existing music library.

Use the **performance** build variant in Android Studio for app performance checks. Build the installable optimized APK with `:app:assemblePerformance` and use `app/build/outputs/apk/performance/app-performance.apk` for delivery. Two additional cold-launch checks were interrupted by another application's overlay and were excluded. The completed optimized trace confirms AuraPlay held input focus throughout its comparison run.

## Player, lyrics, and widget follow-up — November 24, 2026

- Settings → Widget styles now includes **Add to home screen**. Supported launchers show Android's pinning prompt. Other launchers display instructions for opening the widget picker. Compact, Large, and Artwork layouts use rounded playback controls and resize their artwork to keep song details readable.
- The supplied purple play logo replaces the adaptive, square, and round launcher icons. Its transparent foreground also supplies the themed icon shape.
- The player header opens **Audio info** with file name, location, type, bitrate, size, duration, codec, sample rate, and channel count when available. Estimated bitrates are labeled. **Speed** appears beside Equalizer, Bookmarks, and Sleep timer. Song editing remains available from the library's song menu.
- Lyrics lookup handles null provider fields, Unicode metadata, alternate titles, and downloaded filenames. It checks embedded lyrics and sidecar LRC files, ranks online matches using artist and duration, and allows manual search or importing UTF-8 and UTF-16 lyrics files. Explicit refresh can replace imported lyrics with a successful online match. Songs without a matching source can still use imported lyrics.
- Library list publication runs on a background scope. Artwork work is bounded, preference changes avoid unrelated library processing, and lyrics load when their view opens. The global theme applies each resolved accent directly.

## Typography update — November 25, 2026

AuraPlay bundles Manrope across all Material text styles. Headings, song details, controls, and dialogs share the same family with distinct sizes and weights. Audio info uses a semibold filename, a smaller location line, clearer field labels, and the app's neutral surface. The font license and upstream font log are packaged with the application.

## Widget shape and alignment update — November 25, 2026

Settings → Widget styles offers Compact, Large, Square (2×2), and Artwork layouts with live previews. Select Square before adding a widget to request the square launcher slot. Straight, Soft, Rounded, and Extra rounded corners can be selected globally or saved for individual widgets through launcher configuration. Backup and restore include the default corner choice. Older settings retain rounded corners.

The compact widget's playback controls now share the artwork's vertical center. A narrow layout keeps all three 48dp controls visible at the minimum supported width. Widget backgrounds identify their custom outlines to compatible launchers so the selected corner shape is retained on the home screen.

All 58 JVM tests passed. Five native widget checks passed on the Pixel 7 Pro, covering compact alignment, minimum square and narrow sizes, corner outlines, and saved per-widget appearance. The optimized APK and Android test APK built successfully, and lint reported zero errors with 71 warnings.

## Compilation before first launch — November 25, 2026

The optimized APK contained its Baseline Profile, including Compose and Coil paths, but the APK-only installation on the Pixel still reported ART status `verify`. AGP also generated version-specific `.dm` files beside the APK. The Actions artifact uploaded only the APK, so those install-time compilation files were omitted. ProfileInstaller queues an embedded profile several seconds after launch, and Android can compile it later during idle optimization. Installing the matching APK and DEX metadata together makes the profile available during installation. [Android profile installation guidance](https://developer.android.com/topic/performance/baselineprofiles/manually-create-measure)

Installing the same optimized APK with its matching `.dm` changed the Pixel's state to `speed-profile` with reason `install-dm`. In the paired 12-second traces, the first frame fell from 149 ms to 87 ms, its initial measurement work from 67 ms to 42 ms, and app baseline JIT compilation time from 1,330 ms to 247 ms. The 95th-percentile scrolling frame fell from 9 ms to 8 ms. These measurements describe the connected Pixel and its existing library.

The **AuraPlay-Optimized-APK** Actions artifact now includes the complete APK output, its metadata, the Android-specific profiles, and `scripts/install-optimized.ps1`. Extract it and run `./scripts/install-optimized.ps1` from the extracted root in PowerShell. The script checks the profile's DEX keys and checksums against the APK, selects the device's Android version, installs the pair together on API 28+, and reports compilation state. Android 8 devices use the APK update path. Android Studio's **performance** variant and `./gradlew installPerformance` also install the generated metadata. Debug builds remain intended for development.

Installer checks passed in Windows PowerShell 5.1 and PowerShell 7 with simulated Android API levels 27, 30, and 31. A simulated extracted Actions artifact resolved its bundled files correctly, and mismatched APK/profile checksums were rejected before installation.

## Font loading and cold-launch verification — version 1.1.3

The bundled variable font's five weights used Compose's default blocking resource loader. First-use resource loading could therefore happen inside UI layout. The family now uses an asynchronous loader that explicitly moves resource reads and weight creation to `Dispatchers.IO`. Application startup preloads the family into Compose's shared cache. A mutex avoids concurrent parsing of the same cold resource. The font, sizes, weights, and system fallback for Bengali remain the same.

After the font change and installation with matching metadata, the Pixel's longest launch frame was 61 ms and initial measurement took 22 ms. Three additional cold launches produced the following complete launch-and-scroll results. Each cold run force-stopped the app, opened the existing library, and immediately performed six swipes. Each warm run returned up the list and repeated those swipes in the same process.

| Run | Cold frames | Cold jank | Cold p95 | Warm frames | Warm jank | Warm p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 361 | 0.55% | 9 ms | 368 | 0.27% | 8 ms |
| 2 | 339 | 0.59% | 8 ms | 355 | 0.28% | 9 ms |
| 3 | 323 | 0.62% | 8 ms | 381 | 0.26% | 8 ms |

The retained launch frame is still longer than a display interval. These checks establish improvement on this Pixel and library, rather than a guarantee for every device. `scripts/measure-startup-scroll.py` reproduces the comparison, rejects debuggable builds, stops gestures when AuraPlay loses window focus, and writes raw frame reports plus a JSON summary.

Validation passed with 58 JVM tests, the device font regression test, both APK builds, and lint with zero errors and 71 existing warnings. The font test verifies that all five weights resolve to the bundled family after preloading and that regular and extra-bold weights render different glyphs. The actual installer succeeded on Android API 37 and reported `speed-profile` / `install-dm`. Version 1.1.3 (code 5) is installed on the connected phone with existing app data retained.
