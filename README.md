<div align="center">

<img src="branding/app_icon_render.png" width="140" alt="TorrentClaw Movies icon" />

# TorrentClaw Movies

**Movie discovery + magnet links, built properly.**
Kotlin · Jetpack Compose (Material 3) · Hilt · Retrofit · Room · DataStore · Coil

</div>

---

## What this is

A production-shaped Android app for discovering movies through the public
[TorrentClaw](https://torrentclaw.com) API: trending titles, instant search with
quality/sort filters, a detail screen with TMDB metadata and every indexed
release, plus one-tap Open / Copy magnet actions and an offline watchlist.

It is **not** a demo: there are no `TODO`s, every state (loading, empty, error,
offline, rate-limited, no torrent client installed) is implemented, and the
README documents the exact commands, versions and gotchas needed to build it.

**Downloads are real.** Releases expose a **Download .torrent** action that
streams the file from `/api/v1/torrent/{infoHash}` with byte-level progress,
pause/resume/cancel, a resumable `Range` transfer, and a Room-persisted queue that
survives process death. Finished files land in `Downloads/TorrentClaw` and open in
your torrent client via a `FileProvider` handoff.

> ⚠️ **Legal note.** TorrentClaw is a metadata indexer. This app ships no media,
> hosts no files and runs no download engine — it hands magnet URIs to whichever
> torrent client you already installed. You are responsible for what you
> download in your jurisdiction.

---

## Get the APK

| What | Where |
| --- | --- |
| **Signed-ish release APK** (recommended) | [Releases → v1.0.0](https://github.com/surafel5509-del/21-Movie/releases/latest) → `TorrentClawMovies-v1.0.0-release.apk` |
| **Debug APK** (latest commit) | [Actions](../../actions) → newest green *Android CI* run → artifact `TorrentClawMovies-debug-apk` |

Requirements: Android 8.0 (API 26) or newer. Enable "Install unknown apps" for
your browser/file manager. The release APK is signed with the debug key unless
you configure the `KEYSTORE_*` secrets, so it is meant for sideloading and
testing, not for the Play Store.

## Screens

| Home / Discovery | Search & filters | Detail & magnet | Watchlist |
| --- | --- | --- | --- |
| Hero banner for the #1 trending title, rating/quality/seeder badges, 2-column poster grid, skeleton loading, offline notice | 400 ms debounced search, quality chips (All/720p/1080p/2160p), sort chips (Seeders/Newest/Size), empty + rate-limit states | Collapsing backdrop header, poster, synopsis, genres, bookmark toggle, releases sorted by swarm health, Open/Copy magnet per release | Room-backed, fully offline, swipe-free removal with Undo |

---

## Downloads — how it actually works

```
Detail → "Download .torrent"
  → DetailViewModel.onDownloadTorrent(release)
  → DownloadManager.enqueueTorrentFile(movie, release)   // row in Room, status=QUEUED
  → DownloadManager.start(id)                            // ≤2 concurrent, Semaphore
  → FileDownloader.download(url, resumeFrom = staging.length())
        GET https://torrentclaw.com/api/v1/torrent/{infoHash}
        Range: bytes=<offset>-          ← resumes after pause/kill
        writes 64 KB chunks, syncs fd, reports ~4×/s
  → publishToDownloads()                                 // MediaStore (API 29+) or legacy dir
  → status=COMPLETED, path=Downloads/TorrentClaw/…torrent
  → "Open" → FileProvider Uri → torrent client
```

Design decisions worth knowing:

| Decision | Why |
| --- | --- |
| App downloads the `.torrent` itself, not the movie bytes | The movie bytes come from the swarm; a `.torrent` is a small HTTP file the app can legitimately and reliably fetch. |
| No embedded BitTorrent engine | Shipping a native engine means a foreground service, wake locks, battery/thermal testing and a legal surface this app does not claim. Media transfer is delegated to the user's client through the standard magnet/.torrent handoff. |
| `FileDownloader` stages to `cacheDir` then publishes | A partial file never shows up as a real download; publishing is atomic-ish per platform rules. |
| Progress persisted in Room, not just in memory | The queue survives process death; `TorrentClawApp` calls `resumePending()` on start. |
| Semaphore(2) | Two parallel transfers keep the UI responsive and mobile data sane. |
| Real `Migration(1, 2)` | Adding the downloads table must not wipe existing watchlists. |

Progress, speed and ETA are computed in `FileDownloader` (smoothed over 250 ms
windows) and written through `DownloadDao.updateProgress`, so the Downloads tab
is a pure function of Room state.

## Architecture

```
app/src/main/java/com/torrentclaw/movies
├── TorrentClawApp.kt          Application (@HiltAndroidApp) + Coil ImageLoader
├── MainActivity.kt            single activity, edge-to-edge + splash hand-off
├── domain/model/              Movie, MovieDetail, TorrentRelease, SearchFilters, UserPreferences
├── network/
│   ├── TorrentClawApi.kt      Retrofit interface for /popular /recent /search /credits /track
│   ├── AuthInterceptor.kt     X-API-Key + Authorization: Bearer + client fingerprint
│   ├── ApiResult.kt           sealed result type + safeApiCall() + error → message mapping
│   ├── MagnetResolver.kt      rebuilds missing magnet URIs from the infoHash
│   └── dto/                   kotlinx.serialization DTOs and DTO → domain mappers
├── data/
│   ├── local/                 Room: Watchlist, CachedMovie, Download entities/DAOs, migrations
│   ├── download/              FileDownloader (resumable HTTP) + DownloadManager (queue)
│   ├── prefs/                 DataStore: theme, dynamic color, quality, sort, haptics
│   └── repository/            MovieRepository (network + cache), WatchlistRepository (Room)
├── di/                        Hilt modules: network, database, repositories, dispatcher
└── ui/
    ├── theme/                 cinematic dark palette, typography, shapes, dynamic color
    ├── navigation/            type-safe destinations + NavHost + bottom bar
    ├── components/            PosterImage, MovieCard, badges, hero, skeletons, formatters
    ├── util/                  MagnetLauncher (intents/clipboard), Haptics
    ├── home/ search/ detail/ watchlist/ downloads/ settings/   ViewModel + Screen per feature
    └── MainViewModel.kt       theme + splash readiness
```

**Rules the code follows**

- **UDF everywhere.** Screens render an immutable `XxxUiState`; all mutations go
  through the ViewModel. No `mutableStateOf` in a ViewModel, no repository calls
  from a composable.
- **One-way dependencies.** `ui → domain ← data`, and `domain` is pure Kotlin with
  no Android imports, so it is unit-testable on the JVM.
- **No raw exceptions escape the data layer.** Every call returns `ApiResult`,
  which carries a user-presentable message plus a `retryable` flag.
- **Offline is a first-class state**, not an afterthought: popular results are
  cached in Room and served when the network fails; the watchlist never needs
  the network at all.

---

## Tech stack

| Concern | Choice | Version |
| --- | --- | --- |
| Language / build | Kotlin, AGP, Gradle | 2.0.21 · 8.7.3 · 8.9 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| DI | Dagger Hilt | 2.52 |
| Network | Retrofit · OkHttp · kotlinx.serialization | 2.11.0 · 4.12.0 · 1.7.3 |
| Async | Coroutines + StateFlow | 1.9.0 |
| Images | Coil (`SubcomposeAsyncImage`) | 2.7.0 |
| Storage | Room · DataStore Preferences | 2.6.1 · 1.1.1 |
| Navigation | Navigation Compose (type-safe serialization routes) | 2.8.5 |
| Downloads | OkHttp streaming + `FileProvider` + MediaStore | — |
| Min / target / compile SDK | 26 / 35 / 35 | — |

---

## Getting started

### 1. Requirements

- Android Studio Ladybug (2024.2.1) or newer, JDK 17
- Android SDK Platform 35, build-tools 35.0.0
- A device/emulator on API 26+

### 2. Configure the API key (optional but recommended)

TorrentClaw is rate limited per tier:

| Tier | Requests / min | Requests / day |
| --- | --- | --- |
| Anonymous (no key) | 30 | unlimited |
| Free (key) | 120 | 1,000 |
| Pro (key) | 1,000 | 10,000 |

Add your key to **`local.properties`** (already git-ignored):

```properties
sdk.dir=/Users/you/Library/Android/sdk
torrentclaw.apiKey=tc_live_xxxxxxxxxxxxxxxx
```

Alternatives, checked in this order — `local.properties`, then a Gradle
property, then an environment variable:

```bash
./gradlew assembleDebug -Ptorrentclaw.apiKey=tc_live_xxx
TORRENTCLAW_API_KEY=tc_live_xxx ./gradlew assembleDebug
```

The key becomes `BuildConfig.TORRENTCLAW_API_KEY` and is injected by
`AuthInterceptor` as **both** `X-API-Key` and `Authorization: Bearer` (the API
accepts either). With no key the app still works, just at 30 req/min.

### 3. Build & install

```bash
git clone <this repo> && cd 21-Movie
./gradlew :app:assembleDebug          # APK → app/build/outputs/apk/debug/
./gradlew :app:installDebug           # to a connected device
./gradlew test                        # 20+ JVM unit tests
./gradlew lint                        # Android Lint
```

`local.properties` is required (`sdk.dir=...`); Android Studio writes it for you
on first sync. The Gradle wrapper (`gradlew`, `gradle-wrapper.jar`,
`gradle-wrapper.properties`) is committed, so no local Gradle install is needed.

### 4. Release build

```bash
./gradlew :app:bundleRelease   # AAB, minified + resource-shrunk
```

You must supply a signing config (see `docs/ARCHITECTURE.md#release-signing`).

---

## CI: build the APK on GitHub Actions

**Status: green.** CI builds and tests the app on every push, and
[v1.0.0](https://github.com/surafel5509-del/21-Movie/releases/tag/v1.0.0) ships
installable APKs.

`.github/workflows/android-ci.yml` runs three jobs:

| Job | What it does | Artifact |
| --- | --- | --- |
| `test` | `:app:testDebugUnitTest` + `:app:lintDebug` | reports |
| `build-debug` | `:app:assembleDebug` | `TorrentClawMovies-debug-apk` |
| `build-release` | `:app:assembleRelease` (tags `v*` or manual dispatch) | `TorrentClawMovies-release-apk`, attached to the GitHub Release |

On failure the first ~60 lines of the Gradle output are posted as a **commit
comment** (`Error`/`e:`/`What went wrong` lines only), so a red build is
readable without downloading logs.

Push to any branch for a debug APK, or cut a tag for a release:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Optional repository secrets:

| Secret | Purpose |
| --- | --- |
| `TORRENTCLAW_API_KEY` | Baked into `BuildConfig` so CI builds use the 120 req/min tier |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` — enables real release signing |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Keystore credentials |

Without a keystore the release build falls back to the debug key, so the APK is
still installable for testing.

## API integration notes

The app targets the **public TorrentClaw v1 API** at `https://torrentclaw.com/`:

| App action | Endpoint | Notes |
| --- | --- | --- |
| Home hero + grid | `GET /api/v1/popular?limit=24` | Add `type=movie` filtering client-side; response mixes shows |
| "Fresh releases" | `GET /api/v1/recent?limit=18` | Newly indexed titles |
| Search | `GET /api/v1/search?q=…&type=movie&quality=…&sort=…` | `sort ∈ relevance|seeders|year|rating|added` |
| Detail | `GET /api/v1/search?q=<title>&type=movie` | TorrentClaw has **no** `/movie/{id}` route; the app pins the exact content id from the search envelope, which returns full metadata + the nested `torrents` array |
| Credits | `GET /api/v1/content/{id}/credits` | Optional director/cast enrichment |
| Magnet analytics | `POST /api/v1/track` | Fire-and-forget; failures ignored |

Fields that arrive **stringified** (`ratingImdb`, `ratingTmdb`, `sizeBytes`,
`tmdbId`) are parsed defensively in `network/dto/DtoMappers.kt`. Releases with
neither a `magnetUrl` nor a usable `infoHash` are dropped rather than shown as
dead buttons; when only the hash is present, `MagnetResolver` rebuilds a full
magnet URI with a public tracker list.

### Rate-limit strategy

- **400 ms debounce** on the query stream (`SearchViewModel.DEBOUNCE_MILLIS`).
- Chip taps bypass the debounce (0 ms) — they are discrete, intentional actions.
- `flatMapLatest`-style cancellation: each new query supersedes the in-flight one.
- 429 responses surface a *rate limited* UI state with a retry, never a crash.
- `/popular` is cached in Room for 15 minutes, so returning to Home does not
  spend a request.

---

## The magnet flow (and why it is subtle)

```kotlin
val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri)).apply {
    addCategory(Intent.CATEGORY_BROWSABLE)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
```

- On **Android 11+** the package manager hides other apps unless the manifest
  declares package visibility. Without the `<queries>` block for
  `scheme="magnet"`, `resolveActivity()` always returns `null` and Open magnet
  looks broken even with qBittorrent installed. The manifest declares it.
- `ActivityNotFoundException` and `SecurityException` are both caught, and when
  no client exists the magnet is **copied to the clipboard** before a friendly
  snackbar explains what to install — the user never loses the link.
- Clipboard writes happen through `ClipData.newPlainText`; haptics fire only if
  the user enabled them (Settings → Haptic feedback).

---

## Project conventions

- **`libs.versions.toml`** is the single source of dependency truth; module
  build files never hardcode a version.
- **`BuildConfig`** carries `TORRENTCLAW_API_KEY`, `TORRENTCLAW_BASE_URL` and
  `HTTP_LOGGING_ENABLED` (true in debug, false in release so magnet URIs never
  reach logcat).
- **Room schemas are exported** to `app/schemas/`; future versions must add a
  real `Migration` instead of destructive upgrades.
- **ProGuard/R8** rules keep kotlinx.serialization serializers, Retrofit
  interfaces and Room subclasses.
- **Accessibility**: posters expose title/year/rating to TalkBack, icon buttons
  have content descriptions, and colour is never the only signal (seeder health
  shows numbers *and* colour).

---

## Testing

```bash
./gradlew test
```

| Suite | Covers |
| --- | --- |
| `FormattersTest` | binary file sizes, compact counts, text cleanup, health ratio edge cases |
| `DtoMappersTest` | real TorrentClaw JSON fixture, stringified numbers, magnet rebuild, release sorting/dropping |
| `MovieRepositoryImplTest` | movie-only filtering, Room cache fallback, 429 → retryable error, client-side size sort, detail id pinning |
| `SearchViewModelTest` | 400 ms debounce, short-query guard, chip re-query, rate-limit state, clear |

---

## Docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — layer-by-layer walkthrough,
  data flow diagrams, error taxonomy, release signing, known trade-offs.
- [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md) — palette, typography,
  component inventory, motion and accessibility rules.

## Credits

Metadata and torrent indexing by [TorrentClaw](https://torrentclaw.com); movie
artwork and metadata originally from TMDB. Launcher icon and splash artwork are
generated by `branding/generate_icons.py` (Pillow) — rerun it after any palette
change.
