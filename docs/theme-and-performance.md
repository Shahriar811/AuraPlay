# Theme and performance update

## Scrolling repair — October 23, 2026

Recent UI-fix APKs were delivered from the debug variant. Daily-use APKs should be built with `:app:assemblePerformance` and copied from `app/build/outputs/apk/performance/app-performance.apk`. This retains release optimization, resource shrinking, compiled dependency baseline profiles, and the existing local signing key. Debug APKs are intended for development.

Song rows now use a standard ripple and apply their background/border only to the playing song. This removes two color-animation states, the row's press-animation coroutine, and its extra scale/clip layer from each visible list item. The thumbnail, title, options menu, current-song colors, and mini-player gestures remain available.

Library list equality checks run on a background dispatcher. Shuffled queue traversal now uses a visited array instead of repeated list membership scans; periodic playback snapshots therefore take linear time in the queue length on the player thread. Regression tests cover a 10,000-item shuffled queue, natural ordering, repeated timeline positions, and incomplete timelines.

Physical-device frame timing has not been measured in this repair because no Android device is connected.

Validation: all 46 JVM tests passed; debug lint has zero errors and 74 existing warnings; the optimized build and its release lint checks succeeded. The performance APK is non-debuggable, contains compiled `baseline.prof` / `baseline.profm` assets, and its verified APK v2 signature matches the previously delivered APK. Delivery target: `D:\AuraPlay.apk`.

The supplied image guides the charcoal background, soft green accents, rounded home cards, floating mini-player, artwork-led player, and pill controls. Daily mix shuffles the local library, Liked Songs opens favorites, and On repeat opens listening history.

## Launch and scrolling

- A cold library launch no longer constructs ExoPlayer or starts the playback service. Playback connects on demand; existing playback reconnects when the activity starts.
- One shared Room observer feeds the library and history. Filtering, sorting, and collection grouping run on a background dispatcher. Only the visible collection is subscribed.
- Returning home does not repeat a successful library scan. Explicit Refresh remains available.
- Artwork requests use bounded thumbnail sizes, reuse requests, and avoid scrolling crossfades. Palette results are cached and temporary bitmaps are recycled.
- The background is static. Position ticks redraw the mini-player progress track without recomposing home or its playback controls. The player waveform stops animating when paused.
- Release builds enable R8 and resource shrinking. The `performance` variant provides an installable, non-debuggable APK with the same optimizations and shell profiling enabled.

These remove concrete sources of startup contention and repeated UI work. Device-specific lag has not been measured in this environment. Android's [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance) recommends measuring optimized builds.

## Correctness fixes

- Scan after audio permission is granted; show scan progress, failures, and a settings route after permanent denial.
- Keep cached songs when MediaStore queries fail, prevent overlapping scans, and honor cancellation.
- Sort by the actual date added and compare names without case sensitivity. Keep identically titled albums separate by identity and honor short-audio filtering in collections.
- Preserve concurrent favorites/history during metadata scans. Toggle favorites atomically and chunk bulk removals for SQLite's parameter limit.
- Replace destructive upgrades with migrations that retain playlists, favorites, history, and lyrics.
- Keep songs when storage deletion fails; retry after Android 10 consent and request write access for Android 8–9 deletion. Remove lyrics with a deleted song in one transaction. Buffer consent requests while the activity is stopped.
- Release pending controller connections when the activity stops. Wait for a playback connection before opening the player. Clear the song when the queue is emptied or the service is destroyed.
- Keep current state in seek gestures and equalizer slider callbacks. Add queue move controls and preserve queue album metadata.
- Restore playback speed when the service starts. Let Media3 handle audio focus and headphone removal. Apply end-of-track timers to automatic completion, repeat, and the final track; restore volume when replacing a fading timer.
- Refresh widgets when playback changes and open the app when a widget has no active song.
- Keep player database subscriptions stable. Load complete song metadata for lyrics, cancel obsolete requests, retain offline caches after failed refreshes, and correctly parse offsets, repeated timestamps, fractional timestamps, and empty timed lines.
- Observe equalizer session availability, use the device's band count, and clamp levels. Format long tracks with hours and negative times as zero. Apply consistent, readable accents across themes.

## Validation

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assemblePerformance :app:lintDebug --console=plain --max-workers=2
```

The 15 JVM tests cover time formatting, collection identities, and lyrics. Android database tests cover concurrent scan/favorite/history updates, atomic toggles, and version-5 migration with an existing playlist.

Earlier theme-update result: debug APK, Android test APK, optimized APK, and lint all built successfully; 15 JVM tests passed. Lint reports zero errors, with 66 warnings remaining, primarily dependency updates and existing resource/style notices. The optimized APK's signature was verified, its manifest is non-debuggable, and packaged ART baseline profile assets were confirmed. APK size after the follow-up fixes: 3,098,965 bytes.

No connected Android device, configured AVD, or emulator system image was available. Android tests can be compiled here; execution, visual checks, and performance measurement require a device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:installPerformance
```

For comparison, use the same music library and repeat force-stop, cold launch, and scrolling. Compare the first five seconds with a later scroll. Also check locked-screen playback, headphone removal, permission denial/regrant, and upgrading with saved playlists. The performance APK uses the local debug signing key for installation; production signing remains separate.

## Search, player layout, navigation, and reset follow-up

- Search is pinned below the home header, requests keyboard focus when opened, and brings the library back to the top. It stays visible after scrolling.
- Artwork and lyrics now share one flexible viewport above the same anchored player controls. Track title and artist each occupy one line, with ellipsis for long text; waveform space remains reserved. Landscape scrolls controls independently when necessary.
- Home, Stats, and Playlists selections close nested screens and open the requested root, preventing Settings or another child screen from being restored over it. Home remains the Back destination from other tabs.
- Album artwork appears first in the accent picker; the saved selection is preserved.
- Each frequency band has a reset button. Frequency Bands, Bass Boost, Virtualizer, and Reverb also have independent section resets, while the top action still resets everything. Frequency labels use Hz rather than the API's millihertz.
- Compose regression tests exercise opening search from a scrolled library, stable player-control geometry for long titles/lyrics, and root tab selection from nested screens. They require a connected Android device to execute.

Follow-up validation: optimized APK build, 15 JVM tests, UI-test APK compilation, and lint succeeded. Lint has zero errors. The verified APK was copied to `D:\AuraPlay.apk`; device UI test execution remains pending because no device or emulator is connected.


## Version 1.1: ten feature additions

See [the feature guide](features-1.1.md) for locations, persistence details, supported audio tags and device checks. The additions are resume after restart, full queue actions/drag reorder, portable backups, smart playlists, folder inclusion/exclusion, an app-only song/cover editor, listening-time statistics, optional two-decoder crossfade/ReplayGain, three widget styles and named audio bookmarks.

Room upgrades to version 7 with data-preserving migrations. Playback snapshots include duplicate queue occurrences and shuffle order. Saved playback/EQ writes are serialized; settings changes use atomic updates. Library restoration and artwork decoding run in the background. Timestamp history counts actual playback and splits at local midnight, including DST boundaries.

Final version 1.1 validation: debug APK, Android test APK, optimized performance APK and lint built successfully. All 31 JVM tests passed with zero failures or errors. Lint has zero errors and 86 warnings (existing notices, dependency/API updates and style suggestions). Android device tests remain unexecuted because no connected device is available.

The optimized APK is versionCode 2 / versionName 1.1, non-debuggable, includes compiled ART baseline profiles and passes ZIP integrity and APK v2 signature verification with the existing local debug signing key. Its size is 3,200,245 bytes. SHA-256: `3aa3899162b98c0f851d18002e34a60af75ef81c84796bb03ea44123f87a3e66`. The requested destination is `D:\AuraPlay.apk`.


## Version 1.1.1: playback startup repair

The new ReplayGain processor attempted to copy Media3's shared empty buffer into itself at unity gain. `DefaultAudioSink.processBuffers()` drains the processing pipeline before supplying the first audio frame; that drain sends `AudioProcessor.EMPTY_BUFFER` to the processor. `BaseAudioProcessor` initially uses that same buffer, so `output.put(input)` threw `IllegalArgumentException: The source buffer is this buffer`. This prevented playback even with ReplayGain set to Off and was incorrectly presented as an unavailable audio file.

`ReplayGainProcessor.queueInput()` now returns immediately for empty input. PCM samples and gain calculations remain unchanged. A shared Play action now calls `prepare()` before `play()` when an existing queue is idle after an error. The player, mini-player, queue and widget use that retry behavior. Playback errors now log their full cause and distinguish permission, missing-file, decoder and output errors in the user message.

Reproduction before the repair: the real Media3 `AudioProcessingPipeline` failed its empty-startup-buffer test with the same self-copy exception. After the repair, all four pipeline tests pass, covering empty startup drains, bit-transparent unity gain, clipping/gain, reconfiguration and reset between songs. All 35 JVM tests pass. Android test sources compile; no Android device is connected for a live playback check.

Final validation: debug APK, Android test APK, optimized performance APK and lint all built successfully. All 35 JVM tests passed; lint has zero errors and 86 warnings. The APK is versionCode 3 / versionName 1.1.1, non-debuggable, with ART baseline profiles and a verified APK v2 signature matching the prior build. Size: 3,200,245 bytes. SHA-256: `849b76c07b596b83d05e7801fd533055cafdefa53c09d463d0ae13921ddf01f5`. Delivery destination: `D:\AuraPlay.apk`.
