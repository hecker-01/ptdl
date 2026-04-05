# Update Process & Settings Badge — How It Works

## Overview

The update system is **fully self-contained** — no WorkManager, no Services, no Retrofit, no DataStore. It uses a coroutine for the network check, `DownloadManager` for the download, and a `BroadcastReceiver` for install. Everything is triggered once at app start.

---

## 1. Entry Point — `MainActivity.onCreate()`

[app/src/main/java/dev/heckr/ptdl/MainActivity.kt](app/src/main/java/dev/heckr/ptdl/MainActivity.kt)

Two things happen here:

**A) The update check is started:**

```kotlin
UpdateChecker.check(this)
```

**B) The settings badge listener is registered:**

```kotlin
val updateBadge: () -> Unit = {
    if (UpdateChecker.updateAvailable) {
        val badge = bottomNav.getOrCreateBadge(R.id.navigation_settings)
        badge.isVisible = true
        badge.clearNumber() // red dot, no number
    } else {
        bottomNav.removeBadge(R.id.navigation_settings)
    }
}
UpdateChecker.addListener(updateBadge)
updateBadge() // check immediately in case already detected
```

The badge callback is registered as a listener on `UpdateChecker`. When the check finishes, `UpdateChecker` calls all listeners on the main thread — including this one, which shows/hides the red dot.

---

## 2. Version Check — `UpdateChecker` (Singleton)

[app/src/main/java/dev/heckr/ptdl/settings/UpdateChecker.kt](app/src/main/java/dev/heckr/ptdl/settings/UpdateChecker.kt)

### What it does

- Fires a `CoroutineScope(Dispatchers.IO)` coroutine
- Hits **`https://api.github.com/repos/hecker-01/ptdl/releases/latest`** via raw `HttpURLConnection`
  - Header: `Accept: application/vnd.github.v3+json`
- Gets the current app `versionName` from `PackageManager` (currently `26.4.0` per [app/build.gradle.kts](app/build.gradle.kts))
- Parses JSON `tag_name` field, strips leading `"v"` → `tagVersion`
- Calls `isNewerVersion(current, tagVersion)`:
  - Splits both on `"."`, strips any `-suffix` from each segment, compares as integers left-to-right
  - Returns `true` if the remote version is higher than current
- If newer:
  - Scans `assets[]` array for the first entry whose name ends in `.apk`
  - Stores `browser_download_url` as `latestApkUrl`
  - Sets `latestVersion` and flips `updateAvailable = true`
- Calls all registered listeners back on the **main thread** (`Handler(Looper.getMainLooper()).post`)

### Guards

- Skips if `checking == true` (already in-flight)
- Skips if `updateAvailable == true` (already found one — no point re-checking)

### Public API

```kotlin
UpdateChecker.check(context)          // triggers the network check
UpdateChecker.addListener(fn)         // register a () -> Unit callback
UpdateChecker.removeListener(fn)      // unregister
UpdateChecker.updateAvailable: Bool   // current state
UpdateChecker.latestVersion: String   // e.g. "26.5.0"
UpdateChecker.latestApkUrl: String    // direct download URL to the .apk asset
```

---

## 3. Settings Screen Update Card

[app/src/main/java/dev/heckr/ptdl/ui/settings/SettingsFragment.kt](app/src/main/java/dev/heckr/ptdl/ui/settings/SettingsFragment.kt)  
[app/src/main/res/layout/fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml)

The layout has a `MaterialCardView` (`@id/update_card`) with:

- `@id/update_title` — static label "Check for updates"
- `@id/update_subtitle` — dynamic status text, starts as "Tap to check for new versions"

`SettingsFragment` creates an `AppUpdater` in `onCreate()` and calls `syncFromChecker()` in `onViewCreated()` to immediately reflect any state `UpdateChecker` already found (e.g., if the check completed before the user navigated to Settings).

Tapping the card calls `appUpdater.onUpdateTapped(requireContext())`.

---

## 4. Download & Install — `AppUpdater`

[app/src/main/java/dev/heckr/ptdl/settings/AppUpdater.kt](app/src/main/java/dev/heckr/ptdl/settings/AppUpdater.kt)

`AppUpdater` is bound to the `SettingsFragment` lifecycle and drives things via a state machine.

### State Machine

```txt
IDLE → UPDATE_AVAILABLE → DOWNLOADING → DOWNLOADED
```

| State              | Subtitle text shown                        |
| ------------------ | ------------------------------------------ |
| `IDLE`             | "Tap to check for new versions"            |
| `UPDATE_AVAILABLE` | "Update available: X.Y.Z — tap to install" |
| `DOWNLOADING`      | "Downloading update…"                      |
| `DOWNLOADED`       | "Download complete"                        |

### `onUpdateTapped(context)` Behaviour

| Current state      | What happens                                                                                                   |
| ------------------ | -------------------------------------------------------------------------------------------------------------- |
| `IDLE`             | Registers listener on `UpdateChecker`, calls `UpdateChecker.check()`, sets subtitle to "Checking for updates…" |
| `UPDATE_AVAILABLE` | Calls `downloadAndInstallApk(url, version)`                                                                    |
| `DOWNLOADING`      | Does nothing (tap ignored)                                                                                     |

### Download

- Creates a `DownloadManager.Request`:
  - Destination: `getExternalFilesDir(DIRECTORY_DOWNLOADS)/PTDL-<version>.apk`
  - Notification: `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`
- Listens for completion via `BroadcastReceiver` on `ACTION_DOWNLOAD_COMPLETE`
- Also polls every **500 ms** as a backup (handler-based loop)
- On `STATUS_FAILED` → reverts to `IDLE`, shows error

### Install

- Calls `installApk(context, fileName)` on completion
- **API ≥ O:** checks `canRequestPackageInstalls()`
  - If denied → launches `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`
  - `ActivityResultLauncher` retries install after permission is granted
- Uses **`FileProvider`** (authority: `${packageName}.fileprovider`) to create a content URI for API ≥ N
- Fires `Intent(Intent.ACTION_VIEW)` with MIME `application/vnd.android.package-archive`

---

## 5. Settings Badge (the red dot)

The badge is a Material **`BadgeDrawable`** on the bottom navigation bar's Settings tab.

### How it works

| Event                                                   | Effect                                                                                                                                                 |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `UpdateChecker.check()` runs, finds no update           | No badge (or badge removed if already shown)                                                                                                           |
| `UpdateChecker.check()` finds a newer version           | `badge.isVisible = true`, `badge.clearNumber()` → red dot, no number                                                                                   |
| App is recreated / `MainActivity.onCreate()` runs again | `updateBadge()` is called immediately — if `UpdateChecker.updateAvailable` is already `true`, badge appears right away without waiting for a new check |

### Why there's no number

`badge.clearNumber()` is called explicitly — the dot just signals "something needs attention" without a count.

### When does it go away?

Currently it **only goes away** if `UpdateChecker.updateAvailable` is `false` — which only happens if the check was never run or if the app is reinstalled with the new version (resetting the singleton). There is no explicit "dismiss" action after tapping the update card.

---

## 6. Full Flow Diagram

```txt
MainActivity.onCreate()
    │
    ├─► UpdateChecker.check(context)
    │       │
    │       └─► [IO coroutine] GET github.com/hecker-01/ptdl/releases/latest
    │                   │
    │                   ├── tag_name newer? ──No──► notify listeners (updateAvailable=false)
    │                   │
    │                   └──Yes──► store latestVersion + latestApkUrl
    │                               updateAvailable = true
    │                               notify all listeners on main thread
    │                                       │
    │                                       ├─► updateBadge() → show red dot on Settings tab
    │                                       └─► AppUpdater listener → set subtitle + state = UPDATE_AVAILABLE
    │
    └─► UpdateChecker.addListener(updateBadge)
        updateBadge()  ← immediate check for pre-existing state

User taps Settings tab
    └─► SettingsFragment.onViewCreated()
            └─► appUpdater.syncFromChecker()
                    └─► if UpdateChecker.updateAvailable → state = UPDATE_AVAILABLE, update subtitle

User taps "Check for updates" card
    └─► AppUpdater.onUpdateTapped()
            ├── IDLE state → UpdateChecker.check() + "Checking for updates…"
            └── UPDATE_AVAILABLE state → downloadAndInstallApk(url, version)
                    │
                    └─► DownloadManager.Request → PTDL-X.Y.Z.apk
                            │
                            └─► BroadcastReceiver / 500ms poll
                                    └─► STATUS_SUCCESSFUL → installApk()
                                            │
                                            ├── check canRequestPackageInstalls()
                                            └─► FileProvider URI → ACTION_VIEW (system installer)
```

---

## 7. Key Files Reference

| File                                                                                                | Role                                                       |
| --------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| [settings/UpdateChecker.kt](app/src/main/java/dev/heckr/ptdl/settings/UpdateChecker.kt)             | Singleton; GitHub Releases version check                   |
| [settings/AppUpdater.kt](app/src/main/java/dev/heckr/ptdl/settings/AppUpdater.kt)                   | Fragment-bound; DownloadManager + APK install              |
| [ui/settings/SettingsFragment.kt](app/src/main/java/dev/heckr/ptdl/ui/settings/SettingsFragment.kt) | Hosts AppUpdater, renders update card                      |
| [res/layout/fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml)                   | Update card layout (`update_card`, `update_subtitle`)      |
| [MainActivity.kt](app/src/main/java/dev/heckr/ptdl/MainActivity.kt)                                 | Triggers check, manages settings badge                     |
| [app/build.gradle.kts](app/build.gradle.kts)                                                        | `versionName = "26.4.0"`, `versionCode` = git commit count |
