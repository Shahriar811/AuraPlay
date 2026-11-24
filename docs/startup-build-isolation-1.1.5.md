# Recurring launch scrolling — version 1.1.5

The recurrence was reproduced on November 25, 2026. Although the phone had previously received an optimized installation, Android Studio subsequently installed a debug build under the same `com.example.auraplay` application ID. The launcher icon then opened that debug build as well. Its installed flags included `DEBUGGABLE` and `TEST_ONLY`, and ART reported `run-from-apk` rather than a compiled profile.

The cold-launch trace captured a 464 ms main-thread frame, 307 ms opening the DEX file, and repeated 100–127 ms frames after scrolling began. The first six swipes rendered 75 frames with 62.67% jank and a 250 ms 95th-percentile frame. After warming up, the same process still measured 36.61% jank and a 32 ms 95th percentile. This reproduced the reported first several seconds of poor scrolling.

## Durable build changes

- Debug installations use `com.example.auraplay.debug`, display **AuraPlay Dev**, and have a `-dev` version suffix. Android treats them as a separate app, so development Run/Debug and Apply Changes cannot replace the normal app or its data.
- The normal application ID stays `com.example.auraplay`. The `performance` variant is the default for Studio variant selection.
- The shared **AuraPlay (optimized)** Run configuration invokes `:app:runPerformance`. It installs the optimized APK and its matching profile through AGP, then launches the normal app. This task works independently of an existing cached build-variant selection.
- CI checks that development and normal APKs keep distinct application IDs and labels the development artifact clearly. The device context test checks the development package ID and label.

After syncing an existing project, select **AuraPlay (optimized)** to run the everyday app. Select the **debug** variant for breakpoints in AuraPlay Dev. This follows Android's documented [application-ID separation for build variants](https://developer.android.com/build/build-variants#applicationId).

## Verification

The optimized Run task successfully installed both `app-performance.apk` and `app-performance.dm`, and the phone reported version 1.1.5 with ART `speed-profile` / `install-dm`, without the debuggable flag. A retained cold launch took 186 ms to first display, compared with 1,055 ms for the debug build. Its first six swipes rendered 317 frames with 0.63% jank and a 9 ms 95th percentile.

Three further process-cold launches used the existing library and the same six-swipe sequence from `scripts/measure-startup-scroll.py`:

| Run | Cold frames | Cold jank | Cold p95 | Warm frames | Warm jank | Warm p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 311 | 0.64% | 9 ms | 350 | 0.29% | 7 ms |
| 2 | 313 | 0.32% | 8 ms | 354 | 0.28% | 10 ms |
| 3 | 307 | 0.65% | 8 ms | 344 | 0.29% | 10 ms |

All 58 JVM tests and 23 device package/widget/UI/font tests passed. Debug, optimized, and Android test APK builds succeeded, and lint reported zero errors and 71 existing warnings. Build metadata and the shared Run configuration were checked against their expected application IDs and task name. Reinstalling the development APK afterward left the normal app's update timestamp, non-debuggable state, and compiled profile unchanged. These timings describe the connected Pixel 7 Pro and its library.
