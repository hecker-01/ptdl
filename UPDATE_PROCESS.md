# Update Process & Settings Badge — How It Works

## Overview

The update system is **fully self-contained** — no WorkManager, no Services, no Retrofit, no DataStore. It uses a coroutine for the network check, `DownloadManager` for the download, and a `BroadcastReceiver` for install. Everything is triggered once at app start. When an update is found, the user sees a **changelog dialog** with version info, download size, and rendered markdown release notes before confirming, followed by an **inline progress bar** in the update card.

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
  - Stores the asset's `size` (bytes) as `apkSizeBytes`
  - Stores `json.optString("body")` as `releaseBody` (changelog in Markdown)
  - Sets `latestVersion` and flips `updateAvailable = true`
- Calls all registered listeners back on the **main thread** (`withContext(Dispatchers.Main)`)

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
UpdateChecker.releaseBody: String?    // changelog markdown from the release body
UpdateChecker.apkSizeBytes: Long      // APK file size in bytes
UpdateChecker.clear()                 // reset all state
```

---

## 3. Settings Screen Update Card

[app/src/main/java/dev/heckr/ptdl/ui/settings/SettingsFragment.kt](app/src/main/java/dev/heckr/ptdl/ui/settings/SettingsFragment.kt)  
[app/src/main/res/layout/fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml)

The layout has a `MaterialCardView` (`@id/update_card`) with:

- `@id/update_title` — static label "Check for updates"
- `@id/update_subtitle` — dynamic status text, starts as "Tap to check for new versions"
- `@id/update_progress` — a `LinearProgressIndicator`, initially hidden (`gone`), used to show download/install progress

`SettingsFragment` creates an `AppUpdater` in `onCreate()` and calls `syncFromChecker()` in `onViewCreated()` to immediately reflect any state `UpdateChecker` already found.

### Card tap behaviour

- **IDLE state:** triggers a fresh `UpdateChecker.check()`, subtitle shows "Checking for updates…"
- **UPDATE_AVAILABLE state:** opens the **update dialog** (see §3.1)
- **DOWNLOADING / INSTALLING state:** tap is ignored (card is non-clickable)

### Progress bar behaviour

The `onStateChanged` callback controls both the progress bar and card clickability:

| State                       | Progress bar | Card clickable? |
| --------------------------- | ------------ | --------------- |
| `IDLE` / `UPDATE_AVAILABLE` | Hidden       | Yes             |
| `DOWNLOADING`               | Visible      | No              |
| `INSTALLING`                | Visible      | No              |

The `onDownloadProgress` callback controls the progress bar mode:

| Progress value | Bar mode                         | Subtitle text                                    |
| -------------- | -------------------------------- | ------------------------------------------------ |
| `-1`           | Indeterminate (spinning/pulsing) | "Initializing download…" or "Installing update…" |
| `0–100`        | Determinate (filled to %)        | "Downloading… 42%"                               |

### 3.1 Update Dialog

[app/src/main/res/layout/dialog_update.xml](app/src/main/res/layout/dialog_update.xml)

When the user taps the card in `UPDATE_AVAILABLE` state, a `MaterialAlertDialogBuilder` dialog is shown with:

- **Title:** "Update to X.Y.Z" (from `UpdateChecker.latestVersion`)
- **Download size:** formatted with `Formatter.formatFileSize()` (from `UpdateChecker.apkSizeBytes`)
- **Changelog:** rendered as formatted Markdown via **Markwon** (from `UpdateChecker.releaseBody`), inside a `ScrollView` for long release notes. Falls back to "No changelog available." if body is empty.
- **Positive button ("Update"):** dismisses the dialog, calls `appUpdater.startDownload(context)`, which transitions the state to `DOWNLOADING` and shows the indeterminate progress bar
- **Negative button ("Cancel"):** dismisses the dialog, no action taken

---

## 4. Download & Install — `AppUpdater`

[app/src/main/java/dev/heckr/ptdl/settings/AppUpdater.kt](app/src/main/java/dev/heckr/ptdl/settings/AppUpdater.kt)

`AppUpdater` is bound to the `SettingsFragment` lifecycle and drives things via a state machine.

### State Machine

```txt
IDLE → UPDATE_AVAILABLE → DOWNLOADING → INSTALLING
```

| State              | Subtitle text shown                          | Progress bar                |
| ------------------ | -------------------------------------------- | --------------------------- |
| `IDLE`             | "Tap to check for new versions"              | Hidden                      |
| `UPDATE_AVAILABLE` | "Update available: X.Y.Z — tap to install"   | Hidden                      |
| `DOWNLOADING`      | "Initializing download…" → "Downloading… N%" | Indeterminate → Determinate |
| `INSTALLING`       | "Installing update…"                         | Indeterminate               |

### `onUpdateTapped(context): Boolean`

Returns `true` if the caller should show the update dialog, `false` otherwise.

| Current state                | What happens                                                                                                   | Returns |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------- | ------- |
| `IDLE`                       | Registers listener on `UpdateChecker`, calls `UpdateChecker.check()`, sets subtitle to "Checking for updates…" | `false` |
| `UPDATE_AVAILABLE`           | Nothing (caller shows dialog)                                                                                  | `true`  |
| `DOWNLOADING` / `INSTALLING` | Ignored                                                                                                        | `false` |

### `startDownload(context)`

Called by `SettingsFragment` after the user confirms via the dialog's "Update" button.

1. Sets state to `DOWNLOADING`
2. Fires `onDownloadProgress(-1)` → indeterminate bar
3. Sets subtitle to "Initializing download…"
4. Calls `downloadAndInstallApk(context, url, version)`

### Download

- Creates a `DownloadManager.Request`:
  - Destination: `getExternalFilesDir(DIRECTORY_DOWNLOADS)/ptdl-<version>.apk`
  - Notification: `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`
- Listens for completion via `BroadcastReceiver` on `ACTION_DOWNLOAD_COMPLETE`
- Polls every **500 ms** as a backup (handler-based loop)
  - On `STATUS_RUNNING`: reads `COLUMN_BYTES_DOWNLOADED_SO_FAR` and `COLUMN_TOTAL_SIZE_BYTES`, calculates percentage (0–100), fires `onDownloadProgress(percent)` and updates subtitle to "Downloading… N%"
  - On `STATUS_FAILED` → reverts to `IDLE`, fires `onDownloadProgress(-1)`, shows error
  - On `STATUS_SUCCESSFUL` → proceeds to install

### Install

- On download completion: transitions to `INSTALLING` state, fires `onDownloadProgress(-1)` (indeterminate again), sets subtitle to "Installing update…"
- Calls `installApk(context, fileName)`:
  - **API ≥ O:** checks `canRequestPackageInstalls()`
    - If denied → launches `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`
    - `ActivityResultLauncher` retries install after permission is granted
  - Uses **`FileProvider`** (authority: `${packageName}.fileprovider`) to create a content URI for API ≥ N
  - Fires `Intent(Intent.ACTION_VIEW)` with MIME `application/vnd.android.package-archive`

### Callbacks

```kotlin
var onStateChanged: ((State, String) -> Unit)?     // state + subtitle message
var onDownloadProgress: ((Int) -> Unit)?            // -1 = indeterminate, 0–100 = percent
```

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
    │                   └──Yes──► store latestVersion, latestApkUrl, releaseBody, apkSizeBytes
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
    └─► onUpdateTapped() returns true (UPDATE_AVAILABLE)
            │
            └─► showUpdateDialog()
                    │
                    ├── Dialog shows: version title, download size, rendered markdown changelog
                    │
                    ├── [Cancel] → dialog dismissed, nothing happens
                    │
                    └── [Update] → dialog dismissed
                            │
                            └─► appUpdater.startDownload(context)
                                    │
                                    ├── state = DOWNLOADING
                                    ├── onDownloadProgress(-1) → indeterminate bar
                                    ├── subtitle = "Initializing download…"
                                    ├── card becomes non-clickable
                                    │
                                    └─► DownloadManager.Request → ptdl-X.Y.Z.apk
                                            │
                                            └─► 500ms poll loop
                                                    ├── STATUS_RUNNING → onDownloadProgress(0–100)
                                                    │                    subtitle = "Downloading… N%"
                                                    │                    bar = determinate
                                                    │
                                                    └── STATUS_SUCCESSFUL
                                                            │
                                                            ├── state = INSTALLING
                                                            ├── onDownloadProgress(-1) → indeterminate bar
                                                            ├── subtitle = "Installing update…"
                                                            │
                                                            └─► installApk()
                                                                    │
                                                                    ├── check canRequestPackageInstalls()
                                                                    └─► FileProvider URI → ACTION_VIEW (system installer)
```

---

## 7. Key Files Reference

| File                                                                                                | Role                                                                     |
| --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| [settings/UpdateChecker.kt](app/src/main/java/dev/heckr/ptdl/settings/UpdateChecker.kt)             | Singleton; GitHub Releases version check + changelog/size                |
| [settings/AppUpdater.kt](app/src/main/java/dev/heckr/ptdl/settings/AppUpdater.kt)                   | Fragment-bound; state machine, progress tracking, download + install     |
| [ui/settings/SettingsFragment.kt](app/src/main/java/dev/heckr/ptdl/ui/settings/SettingsFragment.kt) | Hosts AppUpdater, renders update card, shows update dialog               |
| [res/layout/fragment_settings.xml](app/src/main/res/layout/fragment_settings.xml)                   | Update card layout (`update_card`, `update_subtitle`, `update_progress`) |
| [res/layout/dialog_update.xml](app/src/main/res/layout/dialog_update.xml)                           | Update dialog content: download size + scrollable changelog              |
| [MainActivity.kt](app/src/main/java/dev/heckr/ptdl/MainActivity.kt)                                 | Triggers check, manages settings badge                                   |
| [app/build.gradle.kts](app/build.gradle.kts)                                                        | `versionName = "26.4.0"`, `versionCode` = git commit count               |

### Dependencies

| Library                        | Purpose                                                      |
| ------------------------------ | ------------------------------------------------------------ |
| `io.noties.markwon:core:4.6.2` | Renders GitHub release notes (Markdown) in the update dialog |
