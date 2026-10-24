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
