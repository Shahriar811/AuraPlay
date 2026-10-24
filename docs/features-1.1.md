# AuraPlay 1.1 feature guide

## Where to find the ten additions

| Addition | Location and behavior |
| --- | --- |
| Resume after restart | Settings → Playback options. Restores song, position, duplicate queue entries, shuffle order and repeat mode; starts paused. A stopped process may lose the last few seconds between periodic saves. |
| Queue actions | Song menu → Play next / Add to queue. Player → Queue: hold a drag handle to reorder; its menu offers accessible move and remove actions. Order follows shuffle when enabled. |
| Backup and restore | Settings → Backup & restore. Select a ZIP destination or source with Android's document picker. Restore merges playlists, likes, bookmarks, metadata/covers, history, play counts and settings. Missing songs are reported. Audio files are not copied. |
| Smart playlists | Playlists → Smart playlists. Recently added uses the last 30 days; unplayed uses play counts; forgotten favorites uses 30 days; weekly favorites ranks actual listening time over seven days. |
| Folder selection | Settings → Music folders. Include a folder and descendants, or hide it. Exclusions take priority. These are persistent library filters; they do not delete files. |
| Song information editor | Song menu → Edit song information. App-only title, artist, album and cover overrides persist across scans. Restore original information removes overrides and rescans. Camera image orientation is respected. |
| Rich statistics | Stats. Actual playback time, seven daily bars, artist rankings for seven days, calendar-month summary and play counts. Pauses and seeks do not inflate time; sessions split at local midnight. Detailed time tracking starts with this version. |
| Crossfade and ReplayGain | Settings → Playback options. Crossfade is off by default and offers 1–12 seconds. Two PCM decoders overlap; transitions that cannot prepare in time use normal playback. ReplayGain supports embedded track/album gain and peak tags in Vorbis/FLAC and common ID3 frames; it does not analyze untagged files. |
| Widget styles | Settings → Widget styles, or configure a widget from the launcher. Compact, Large and Artwork styles have independent widget configuration and actual cover art. Small sizes use a compact layout. Buttons reconnect to the saved player state. |
| Audio bookmarks | Player menu → Audio bookmarks saves a named moment. Playlists → Audio bookmarks provides search, playback from a timestamp and deletion. |

Equalizer settings now persist. Sound effects follow the incoming crossfade session, and sleep timers take priority over crossfades. Repeat-one does not crossfade a song into itself. Crossfade and ReplayGain remain optional.

## Data preservation

Room version 7 adds song overrides, bookmarks and listening events through a migration from version 6. Existing migration paths from versions 1–5 continue through version 6. There is no destructive migration fallback.

Backups match tracks by file location, unique title/artist/duration, or a unique filename/duration when the library has moved. Ambiguous or unavailable files are skipped. Restore retains duplicate playlist names, and restoring the same backup again does not duplicate playlist tracks, timestamp bookmarks or listening events. Counts use the greater saved value rather than adding the same backup repeatedly. Cover assets are bounded, validated images inside the app's private storage. ZIP paths and input sizes are validated, with temporary files removed after import.

Playback and EQ saves are serialized and conflated so an older write cannot overwrite a newer state. Library restoration, sorting and grouping stay off the UI thread. Widgets reuse the application's image cache and load bounded thumbnails.

## Validation

Build and static results are recorded in `theme-and-performance.md`. JVM tests cover resume/deleted files/duplicate entries/shuffle order, folder ancestry, smart playlist rules, calendar-day statistics, midnight and DST splitting, ReplayGain peak protection and PCM saturation, plus the earlier lyrics/time/library regressions.

Android test sources cover database migration, scan/override races, bookmark uniqueness/cascades, monotonic listening-history writes, portable backup round trips and repeated restore, concurrent preference edits, PCM buffers and a real two-decoder overlap. They also retain the earlier search, player layout and navigation checks.

No connected Android device is available in this environment. Android tests compile but have not executed. Device checks still needed:

1. Cold launch and scroll a large library, then compare with later scrolling in the optimized APK.
2. Play, pause, seek and reorder duplicate tracks with shuffle on and off; restart and check song/position/queue restoration.
3. Listen to tagged and untagged files with ReplayGain in each mode. Test crossfade with short tracks, repeat modes, a missing next file, headphones, audio focus interruptions and sleep timers.
4. Edit a title/cover, rescan, then export and restore twice into a library with changed IDs and moved files; inspect playlists, likes, bookmarks and counts.
5. Configure and resize all three widget styles; test controls after stopping the process and after resuming playback.
6. Check long titles, lyrics, large text, landscape, search focus and bottom tab navigation.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:installPerformance
```
