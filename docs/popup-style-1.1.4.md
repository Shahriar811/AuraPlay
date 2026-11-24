# Popup styling — version 1.1.4

The custom app palette previously set `surface` but left Material 3's surface-container roles at their defaults. Dropdown menus therefore displayed a lavender panel even when the app used a different accent and neutral surfaces.

The theme now supplies every surface-container level, surface tint, and inverse surface used by popup messages. Shared menu and dialog components use those colors with zero tonal elevation, subtle outlines, the bundled Manrope typography, and rounded corners. Menus use 24 dp corners, aligned 20 dp icons, 14 sp medium labels, and standard touch targets. Dialogs use 28 dp corners and the app's title and body text styles. Long menu labels can wrap onto two lines, and destructive actions have a separate group and a readable error color.

The review covered these app-owned popups:

- Song actions in Home, Favorites, playlist details, smart playlists, and listening statistics.
- Library sorting, with an indicator for the active sort order.
- Playlist rename/delete and queue move/remove menus.
- Audio info, lyrics search, playback speed, and sleep timer dialogs.
- Playlist creation, renaming, deletion, selection, and collection-song dialogs.
- Queue and audio-bookmark sheets.

The song editor screen and its save/reset actions have been removed. Song action menus offer read-only Audio info in its place. Existing saved metadata and backup compatibility are retained. A restored navigation state referring to the old editor returns to the previous screen or Home.

Device checks exercise menu colors while switching light/dark themes, selected and disabled actions, 150% text sizing, dialog colors, and opening/dismissing Audio info from a real Home song menu. An older search assertion now targets its song row by ID so it cannot also match the search field containing the same text. Visual previews are generated under the app's external cache and can be retrieved from `popup-previews/` for review.

Validation passed with 58 JVM tests, 15 device UI/font tests on the Pixel 7 Pro, debug and optimized APK builds, the Android test APK build, and lint with zero errors and 71 existing warnings. The large-text check confirms that wrapped action rows grow in height rather than clipping their labels.
