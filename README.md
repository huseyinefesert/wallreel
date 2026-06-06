<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="96" alt="Wallreel icon" />
</p>

<h1 align="center">Wallreel</h1>

<p align="center">
  A live wallpaper app that turns your photo albums into an auto-cycling playlist — with a timer, shuffle, and per-photo scale control.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Min%20SDK-26-blue" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-2024-4285F4" />
  <img src="https://img.shields.io/badge/License-MIT-green" />
</p>

<p align="center">
  <a href="https://github.com/huseyinefesert/wallreel/releases/latest">
    <img src="https://img.shields.io/badge/Download%20APK-latest-brightgreen?style=for-the-badge&logo=android" />
  </a>
</p>

---

## Features

| Feature | Description |
|---|---|
| 📂 **Albums** | Create multiple albums and populate them from your gallery |
| ▶️ **Playlist** | The active album cycles through photos automatically |
| ⏱️ **Timer** | Set your own interval — 15 min, 30 min, 1 hr, up to 1 day, or any custom value |
| 🔀 **Shuffle** | Play photos in random order |
| 🖼️ **Fill / Fit scale** | Album-wide default scale, overridable per photo |
| 👆 **Double-tap** | Double-tap the home screen to skip to the next photo instantly |
| 🔍 **Quick find** | A "Currently on wallpaper" card in the album screen shows the active photo with direct scale access and a "Show in list" scroll button |

---

## How it works

Wallreel is a **Live Wallpaper** — the only Android mechanism that allows touch input (double-tap) on the home screen. You configure albums and settings inside the app, then set it as your live wallpaper once.

```
App (configure) → Live Wallpaper Service (draw + detect double-tap)
                       ↑
              AlarmManager (auto-advance on timer)
```

Photos you add are **copied into app-internal storage** so they remain accessible after reboots or permission changes. Playlist state is stored in `SharedPreferences` so the wallpaper service can read it without a database connection.

---

## Scale modes

| Mode | Behavior |
|---|---|
| **Fill** | Scales the photo to cover the screen completely; crops the overflow (center-crop) |
| **Fit** | Scales the photo so the entire image is visible; letterboxes with black (center-inside) |

Scale can be set at two levels:
- **Album** — the default for all photos in that album
- **Per photo** — overrides the album default for a single photo (options: Album / Fill / Fit)

### Set as live wallpaper
1. Open the app and create an album.
2. Add photos from your gallery (multi-select supported).
3. Tap **Set active** on the album.
4. Configure your timer interval and shuffle preference.
5. Tap **Set as live wallpaper** — confirm in the system picker.

---

## Project structure

```
app/src/main/java/com/efesert/wallreel/
├── data/
│   ├── Models.kt          # Album & Photo Room entities, ScaleMode constants
│   ├── AppDao.kt          # Room DAO
│   ├── AppDatabase.kt     # Room database singleton
│   └── Repository.kt      # Single source of truth; bridges DB ↔ PlaylistController
├── playlist/
│   ├── Prefs.kt           # SharedPreferences wrapper (timer, shuffle, current photo)
│   └── PlaylistController.kt  # Queue build, advance, scale-only update (no position reset)
├── scheduler/
│   ├── WallpaperScheduler.kt  # AlarmManager scheduling
│   ├── AlarmReceiver.kt       # Advances playlist on alarm fire, re-schedules next
│   └── BootReceiver.kt        # Re-schedules alarm after device reboot
├── service/
│   ├── PlaylistWallpaperService.kt  # Live wallpaper: Canvas drawing + double-tap detection
│   └── BitmapUtils.kt              # Efficient bitmap decoding, Fill/Fit matrix calculation
└── ui/
    ├── MainActivity.kt    # Entry point, Compose NavHost
    ├── AppViewModel.kt    # Shared ViewModel; observes DB + current-photo broadcast
    ├── HomeScreen.kt      # Album list, timer picker, shuffle toggle
    ├── AlbumScreen.kt     # Photo grid, "currently on wallpaper" card, scale dialogs
    └── Theme.kt           # Material3 color scheme
```

---

## Tech stack

- **Language:** Kotlin 2.0
- **UI:** Jetpack Compose + Material3
- **Database:** Room 2.6
- **Image loading:** Coil 2.7
- **Async:** Kotlin Coroutines + Flow
- **Navigation:** Jetpack Navigation Compose
- **Background:** AlarmManager (`setAndAllowWhileIdle`) — works under Doze without exact-alarm permission

---

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.
