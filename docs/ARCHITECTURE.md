# Architecture

This document explains *why* the app is shaped the way it is, layer by layer.
If you are reviewing the code, read this first; if you are extending it, follow
the patterns here so the invariants below stay true.

---

## 1. Layer map

```
┌─────────────────────────────────────────────────────────────────────┐
│ ui/                         Compose + ViewModels (UDF)              │
│   home/ search/ detail/ watchlist/ settings/  components/ our theme │
└───────────────▲──────────────────────────────┬──────────────────────┘
                │ UiState (immutable)          │ user intents / actions
┌───────────────┴──────────────────────────────▼──────────────────────┐
│ domain/                     Models + filter enums (pure Kotlin)     │
│   Movie, MovieDetail, TorrentRelease, SearchFilters, UserPreferences│
└───────────────▲──────────────────────────────┬──────────────────────┘
                │ domain objects                │ interfaces
┌───────────────┴──────────────────────────────▼──────────────────────┐
│ data/                       repositories, Room, DataStore           │
│   MovieRepository(Impl)  WatchlistRepository(Impl)  PreferencesRepo  │
└───────────────▲──────────────────────────────┬──────────────────────┘
                │ DTOs → mappers                │ Retrofit calls
┌───────────────┴──────────────────────────────▼──────────────────────┐
│ network/                    TorrentClaw API + ApiResult safety net   │
└─────────────────────────────────────────────────────────────────────┘
```

Invariants:

1. `domain` imports nothing from Android, `data`, `network` or `ui`.
2. `ui` never imports `network.dto` or Retrofit types — it only sees domain models.
3. Repositories return `ApiResult<T>`; nothing above `network` handles exceptions.
4. A ViewModel never exposes mutable state; `StateFlow<UiState>` only.

---

## 2. Unidirectional data flow

```
        intent / event                    reducer / suspend work
Screen ─────────────────▶ ViewModel ──────────────────────────────▶ Repository
   ▲                        │                                          │
   │    StateFlow<UiState>  │                                          │
   └────────────────────────┴────────────── domain models ◀─────────────┘
```

Every screen follows the same shape:

| Piece | Example (`SearchViewModel`) |
| --- | --- |
| Immutable state | `data class SearchUiState(query, filters, movies, isLoading, …)` |
| Inputs | `onAction(SearchAction)` for UI events; `init {}` for initial load |
| Side effects | `StateFlow<Event?>` consumed once (`HomeEvent.ShowMessage`) |
| Derived flags | `val isEmpty get() = hasSearched && !isLoading && …` |

Why a single state object instead of many `StateFlow`s? Because the UI needs
coherent combinations (“loading **and** no results” must never flash the empty
state). One object makes those combinations explicit and testable.

---

## 3. Networking

### 3.1 Retrofit interface

`TorrentClawApi` returns `Response<T>` (not bare `T`) so the safe wrapper can
inspect the status code and produce a typed error.

### 3.2 Authentication

`AuthInterceptor` adds:

```
Accept: application/json
User-Agent: TorrentClawMovies/<version> (Android)
X-Search-Source: android
X-API-Key: <key>              (only if configured)
Authorization: Bearer <key>   (only if configured)
```

Both header styles are sent because TorrentClaw accepts either and users copy
keys from different dashboards. A blank key degrades to the anonymous tier
instead of producing 401s.

### 3.3 Error taxonomy

`ApiResult.Error.type` → UI behaviour:

| Type | Typical cause | UI |
| --- | --- | --- |
| `NETWORK` | `UnknownHostException`, `IOException` | “You're offline” + retry |
| `TIMEOUT` | `SocketTimeoutException` | retry (retryable) |
| `RATE_LIMITED` | HTTP 429 | dedicated rate-limit state, retry after pause |
| `UNAUTHORIZED` | 401/403 | configuration message |
| `NOT_FOUND` | 404 | “couldn't find that movie” |
| `SERVER` | 5xx | retry |
| `PARSE` | `SerializationException`, empty body | retry (the API may rotate fields) |
| `UNKNOWN` | anything else | generic retry |

`safeApiCall { … }` is the only place that catches; cancellation
(`CancellationException`) is always rethrown so structured concurrency works.

---

## 4. Data strategy

### 4.1 Popular feed + cache

```
getPopular(forceRefresh)
  ├── Room cache fresh (< 15 min) and not forced → return cache (0 requests)
  ├── network OK                                   → upsert cache, return fresh
  ├── network fails but cache exists               → return cache (offline mode)
  └── network fails, cache empty                   → ApiResult.Error
```

The cache lives in the `cached_movies` table keyed by `(section, position)` so
ordering is stable across reads. Entries older than three days are evicted on
every write.

### 4.2 Search

Debounce lives in the ViewModel, not the composable, so it survives
recomposition. `combine(queryInput, filterInput)` → `debounce` → `distinctUntilChanged`
→ network. Chip taps emit with a 0 ms window because they are explicit.

### 4.3 Detail

TorrentClaw exposes content by **id**, but lookups happen by **title**
(`contentUrl`). The repository queries the title, then pins the entry whose `id`
matches the tapped poster. If that fails it falls back to the cached popular row
so a user who is offline can still read the synopsis they saw earlier (with an
empty release list).

### 4.4 Watchlist

Plain Room. `WatchlistEntity` stores a denormalised snapshot of the movie
(title, poster, rating, overview) so it renders offline without a network call.
Genres are joined with `\u001F` rather than a converter table — they are
read-only and never queried.

---

## 5. Magnet handling

```
ReleaseCard "Open magnet"
   → DetailViewModel.onOpenMagnet(release)  (emits DetailEffect.OpenMagnet + tracks analytics)
   → DetailScreen LaunchedEffect
   → MagnetLauncher.openMagnet(context, uri)
        ├── malformed            → InvalidLink   → snackbar
        ├── no resolver (queries) → copy to clipboard + NoClientAvailable → snackbar "install a client"
        ├── ActivityNotFoundException → same fallback
        └── launched             → Launched
```

`MagnetLauncher` is a plain object (not a composable) so the intent contract is
unit-testable and appears in exactly one file. Package visibility for
`magnet:` is declared in the manifest — see the `<queries>` block.

---

## 5.5 Downloads

The queue is a Room table plus an in-memory set of `Job` handles:

```
DownloadEntity ──(status)──▶ DownloadManager.jobs[id]
      ▲                              │
      │ updateProgress every ~250 ms  │ Semaphore(2)
      └──────────── FileDownloader ◀──┘
```

- **Resumability.** `FileDownloader` re-opens the staging file, seeks to its
  length, sends `Range: bytes=<len>-`, and only appends on `206 Partial Content`.
  A server that ignores the range triggers a clean restart rather than a corrupt file.
- **Cancellation.** Every read-loop iteration calls
  `currentCoroutineContext().ensureActive()`, so pausing stops I/O in the next
  64 KB, not after the whole body drains. `CancellationException` is translated
  to `PAUSED` (bytes kept), never to `FAILED`.
- **Publishing.** Staged bytes live in `cacheDir/downloads_staging`; only a
  completed transfer is written into MediaStore `Downloads/TorrentClaw`
  (`IS_PENDING` on API 29+, legacy public dir below).
- **Handoff.** `DownloadManager.openTorrentFile` builds a `FileProvider` URI
  (authority `${applicationId}.fileprovider`, paths in `res/xml/file_paths.xml`).
  `file://` URIs are never used — they throw `FileUriExposedException` on API 24+.
- **Concurrency limit.** `Semaphore(2)` bounds simultaneous transfers;
  additional jobs wait in `QUEUED` and are picked up on `resumePending()`.
- **Schema evolution.** v1 → v2 adds the table through a real
  `Migration(1, 2)` with matching indices; there is a unit test asserting the
  entity's id/progress semantics so a refactor cannot silently break the queue.

### Why not an embedded BitTorrent engine

A P2P engine needs a foreground service, wake locks, per-IP tracker handling,
port management and thermal testing — plus it changes the app's legal posture
from "index client" to "downloader". This app instead fetches the `.torrent`
(an ordinary HTTPS resource) and hands it to the client the user already trusts.

## 6. Navigation

Type-safe routes via kotlinx.serialization:

```kotlin
@Serializable data object Home : Destination
@Serializable data object Search : Destination
@Serializable data object Watchlist : Destination
@Serializable data object Settings : Destination
@Serializable data class MovieDetail(val movieId: Int, val movieTitle: String) : Destination
```

`NavHost(startDestination = Destination.Home)` with `composable<T> { }`. The
detail ViewModel reads its arguments from `SavedStateHandle`, tolerating the
value arriving as `Int`, `Long` or `String`.

The bottom bar is hidden for `MovieDetail` (a full-screen destination) by
checking `currentDestination.hierarchy` against the three tab routes. Tab
switches use `saveState`/`restoreState` so scroll positions survive.

---

## 7. Theming

- Dark is the default (`ThemeMode.DARK`); `SYSTEM` and `LIGHT` are opt-in.
- `TorrentClawTheme(themeMode, dynamicColor)` builds either the bespoke palette
  or a Material You scheme (API 31+), then publishes `ExtendedColors` — swarm
  health, rating badge, quality badge, hero scrim — through a
  `staticCompositionLocalOf`, read as `MaterialTheme.extendedColors`.
- System bar icon colours are flipped in a `SideEffect` based on the theme.
- Splash uses `androidx.core:core-splashscreen`; the activity keeps it visible
  until the first DataStore emission arrives (`MainUiState.isReady`), which
  avoids the dark→light flash for light-theme users.

---

## 8. Release signing

`app/build.gradle.kts` intentionally has **no** committed signing config.
Add one locally (or in CI secrets):

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
}
```

Then:

```bash
KEYSTORE_PATH=~/keys/release.jks KEYSTORE_PASSWORD=… KEY_ALIAS=… KEY_PASSWORD=… \
  ./gradlew :app:bundleRelease
```

Release builds enable R8 + resource shrinking; `proguard-rules.pro` keeps
serializers, Retrofit interfaces and Room subclasses.

---

## 9. Known trade-offs

| Decision | Why | Cost |
| --- | --- | --- |
| Refresh button instead of pull-to-refresh | Material 3's `PullToRefreshBox` API moved between BOM releases; an explicit action is stable across versions and testable | Less “native” gesture |
| Client-side “Size” sort | TorrentClaw has no size sort parameter | Size sort happens on the visible page only |
| Client-side `type=movie` filtering | `/popular` and `/recent` have no type parameter | Occasional sparse page if shows dominate |
| Title-based detail lookup | No `/movie/{id}` endpoint exists | One extra search request per detail open |
| Denormalised watchlist rows | Offline-first rendering | Stale metadata until the movie is re-saved |
| No paging library | 24-item pages, single page in UI | Infinite scroll is a follow-up |
| Torrent files, not media bytes | Delegates real P2P transfer to the user's client | The app can't stream from a swarm by itself |
| In-process download manager | No WorkManager dependency; jobs resume on next app start | A download pauses if the process is killed |

## 10. Extending the app

1. **New screen** → `ui/<feature>/<Feature>ViewModel.kt` + `<Feature>Screen.kt`,
   add a `@Serializable` destination, register it in the `NavHost`.
2. **New API field** → add it to the DTO as nullable, map it in
   `DtoMappers`, surface it in the domain model, then use it in the UI.
3. **New preference** → key in `PreferencesRepository.Keys`, field on
   `UserPreferences`, setter, then read it through `MainViewModel` or the
   feature ViewModel.
4. **Schema change** → bump `TorrentClawDatabase.version`, write a `Migration`,
   add a `room-testing` migration test, and commit the exported schema JSON.
