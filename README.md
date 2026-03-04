# PTDL

A native Android app for browsing content downloaded with [patreon-dl](https://github.com/patrickkfkan/patreon-dl). Point it at your local patreon-dl folder and browse creators, collections, and posts entirely offline.

## Features

### Browse & Discover

- **Creator list** - all downloaded creators with avatars, cover images, and post counts
- **Creator pages** - collapsing cover image, profile card, horizontal collections carousel, and a searchable post feed
- **Collections** - view posts grouped by the creator's collections with cover banner
- **Post detail** - rich text content, formatted dates, like/comment counts, and an attachment thumbnail grid

### Image Viewer

- **Fullscreen gallery** - swipe between images with a page indicator
- **Pinch-to-zoom** - smooth 1x–5x zoom with bounded panning
- **Double-tap zoom** - animated zoom in/out with a decelerate transition
- **Memory-efficient** - only keeps the current page in memory, loading and unloading on demand

### Performance

- **Parallel scanning** - creators, collections, and posts are scanned concurrently (8 parallel workers) for fast indexing over SAF
- **Progressive loading** - profile appears first, then collections and posts stream in simultaneously with skeleton placeholders
- **Streaming post flow** - posts emit to the UI as they're parsed, no waiting for a full scan
- **In-memory caching** - thread-safe cache with background warm-up on app start
- **Thumbnail prefetching** - post thumbnails are preloaded as creator cards scroll into view
- **Infinite scroll** - first 5 posts load instantly, more pages of 10 load as you scroll

### UI / UX

- **Material 3 + Dynamic Color** - adapts to your device's Material You wallpaper palette
- **Dark mode** - system, light, or dark theme toggle
- **Edge-to-edge** - full immersive layout with proper inset handling
- **Skeleton loading** - shimmer placeholders for collections and posts while data loads
- **Search** - real-time post filtering by title on the creator page
- **Scroll indicators** - visible scrollbars on all scrollable lists

### Offline-First

- **No network required** - reads entirely from local patreon-dl files via Android's Storage Access Framework
- **No Patreon API calls** - all data comes from the downloaded JSON metadata and media files
- **Persistent access** - folder permission survives app restarts

## Getting Started

1. Download content with [patreon-dl](https://github.com/patrickkfkan/patreon-dl)
2. Install PTDL on your Android device (API 31+ / Android 12+)
3. Open Settings → Select your patreon-dl folder
4. Browse your content

## Tech Stack

|               |                                          |
| ------------- | ---------------------------------------- |
| Language      | Kotlin                                   |
| UI            | View Binding + Material 3                |
| Navigation    | Jetpack Navigation Component             |
| Image Loading | Coil 2.7                                 |
| Concurrency   | Kotlin Coroutines (Flow, Channel, async) |
| Storage       | SAF DocumentFile                         |
| Min SDK       | 31 (Android 12)                          |

## Building

```bash
./gradlew assembleDebug
./gradlew installDebug
```
