# Verification log

This project was authored in a sandbox **without** the Android SDK, Google Maven
or Maven Central (only GitHub/npm/PyPI were reachable), so a full Gradle build
could not run here. To avoid shipping code that "looks right", the following
offline checks were executed against the real sources.

## 1. Kotlin syntax — every file

`tree-sitter-kotlin` was used to parse every `.kt` file in `app/src`:

```
54/54 Kotlin files parse cleanly (0 errors)
```

## 2. Real compilation — pure Kotlin slice

A standalone Kotlin 2.4.21 compiler (obtained via npm) plus a temporary stub for
`kotlinx.serialization` annotations compiled and executed:

`domain/model/*`, `network/dto/*`, `network/MagnetResolver.kt`,
`ui/components/Formatters.kt`, and a harness exercising them.

```
ALL CHECKS PASSED   (41 assertions)
```

Covered: binary size formatting, compact counts, HTML/whitespace cleanup, ratings,
the documented TorrentClaw search payload (stringified `sizeBytes`/`ratingImdb`),
release sorting and dead-release dropping, magnet preservation vs. rebuild,
tracker injection, hex/base32 info-hash parsing, storage-enum round-trips.

## 3. Real compilation — coroutine/data layer

The same compiler, with `kotlinx-coroutines-core` (shipped with the compiler) and
small stubs for Retrofit/Room/DataStore/Hilt/Android annotations, compiled:

```
network/ApiResult.kt, network/TorrentClawApi.kt
data/local/*  data/prefs/*  data/repository/*
ui/home/HomeViewModel.kt  ui/search/SearchViewModel.kt  ui/watchlist/WatchlistViewModel.kt
ui/detail/DetailViewModel.kt  ui/settings/SettingsViewModel.kt  ui/MainViewModel.kt
ui/navigation/Destination.kt
```

```
0 errors
```

### Bugs this actually caught (fixed)

| Bug | Impact if shipped |
| --- | --- |
| `inline` on `ApiResult.map/onSuccess/onError` | **Hard compile error** — inline is illegal on virtual interface members |
| `CachedMovieEntity.toDomain()` never defined | Hard compile error in `MovieRepositoryImpl` |
| `Response.isSuccessful` used as a property | Hard compile error (it is a Java method) |
| `Preferences.Key` referenced through the wrong import | Unresolved reference |
| `Double? → any` `ifEmpty` misuse | Type-inference failure on cached fallback |

## 4. Resource integrity

A script cross-checked every `R.string`, `R.drawable`, `R.mipmap`, `@string`,
`@drawable`, `@mipmap`, `@color`, `@style` and `@xml` reference against the
resource files:

```
✅ every R.* and resource reference resolves
116 strings · 3 drawables · 3 mipmaps · 2 styles
✅ all XML files well-formed
```

## 5. Artwork

`branding/generate_icons.py` produced the legacy launcher icons, adaptive
foreground layers, `app_icon_render.png` and `feature_graphic.png`; the rendered
icon was visually inspected.

## 6. Download layer (added after the first pass)

The same offline compiler setup was extended with OkHttp/MediaStore/FileProvider
stubs to type-check `data/download/*`, `data/local/DownloadEntity.kt`,
`TorrentClawDatabase` (with the v1→v2 migration), `DetailViewModel` and
`DownloadsViewModel`:

```
238 classes emitted, 0 errors
```

### Bugs this caught

| Bug | Impact if shipped |
| --- | --- |
| `onProgress` typed `(DownloadProgress) -> Unit` while the manager calls a suspend DAO | **Hard compile error** — progress persistence could never compile |
| `DownloadsViewModel` missing `import kotlinx.coroutines.flow.asStateFlow` | Hard compile error |
| `@Suppress`-ed dead `BuildConfig.unused()` helper | Dead code / lint noise (removed) |

## 7. Real Gradle build on GitHub Actions

Offline type-checking caught logic and type errors, but only a real build catches
missing imports in Compose files and anything that depends on the actual
AndroidX artifacts. The CI workflow closes that gap.

**Run `43919521984` (push, `arena/9e7548c2-21-movie`) — all jobs green:**

| Job | Result | Evidence |
| --- | --- | --- |
| Unit tests & lint | ✅ | unit-test-report (14 KB), lint-report (35 KB) |
| Build debug APK | ✅ | `TorrentClawMovies-debug-apk`, 18.9 MB |
| Build release APK | ✅ | `TorrentClawMovies-release-apk`, 2.0 MB (minified) |

**Final state — run `43927159608` on `ec5f86e`, all jobs green:** 34 unit tests,
0 failures, 0 skipped; lint report generated; `TorrentClawMovies-debug-apk`
(18.9 MB) uploaded. Every push to `arena/9e7548c2-21-movie` runs the same
pipeline, and tag `v1.0.0` has a published, installable APK attached.

Note: the debug and release APKs are minified differently (18.9 MB vs 2.25 MB)
because the release build runs R8 with resource shrinking, which is expected —
not a sign of a broken artifact.

**Run `43920945000` (tag `v1.0.0`) — all three jobs green**, and
`app-release.apk` (2.25 MB) is attached to
[the v1.0.0 release](https://github.com/surafel5509-del/21-Movie/releases/tag/v1.0.0).

### Real compile errors the CI caught

| File | Error | Fix |
| --- | --- | --- |
| `HomeScreen.kt` | `Unresolved reference 'rememberSnackbarHostState'` | Material 3 has no such function — use `remember { SnackbarHostState() }` as a default parameter |
| `HomeScreen.kt` | `Unresolved reference 'width'` | missing `androidx.compose.foundation.layout.width` import |
| `HomeScreen.kt` | `Unresolved reference 'Explore'` | icon referenced fully-qualified without importing it |
| `DetailScreen.kt` | `Unresolved reference 'MagnetOpenResult'` + non-exhaustive `when` | `MagnetOpenResult` lives in `ui.util`, not as a nested member of `MagnetLauncher` |
| `workflow` | every run failed in 0 s with no jobs | `if: ${{ secrets.X != '' }}` — the `secrets` context is not allowed in `if:`; the check moved into the step |

The last one is worth remembering: GitHub reports that as a run with
`total_count: 0` jobs and an empty log, which looks like "Actions is broken"
rather than "the YAML is invalid".

## What still needs a real Android build

Anything that depends on Compose runtime behaviour or on the exact artifact
coordinates: composable signatures, Material 3 experimental APIs, Hilt/KSP code
generation, and R8. These are pinned to known-good versions in
`gradle/libs.versions.toml`, and the Compose surface deliberately avoids APIs
that moved between BOM releases (no `PullToRefreshBox`, no
`rememberRipple`, no `LineHeightStyle` constructor, no `strokeCap`).

Run these on a machine with the Android SDK (or on GitHub Actions, see README)
before release:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```
