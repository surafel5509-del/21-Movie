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

## What still needs a real Android build

Anything that depends on Compose runtime behaviour or on the exact artifact
coordinates: composable signatures, Material 3 experimental APIs, Hilt/KSP code
generation, and R8. These are pinned to known-good versions in
`gradle/libs.versions.toml`, and the Compose surface deliberately avoids APIs
that moved between BOM releases (no `PullToRefreshBox`, no
`rememberRipple`, no `LineHeightStyle` constructor, no `strokeCap`).

Run this on a machine with the Android SDK before release:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```
