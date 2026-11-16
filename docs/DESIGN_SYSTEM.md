# Design system

A cinema-dark product surface: near-black canvases so poster art carries the
colour, one warm gold accent, and a teal/violet support pair. Everything here is
implemented in `ui/theme/`.

---

## Palette

### Dark (default)

| Token | Hex | Use |
| --- | --- | --- |
| `primary` (Gold) | `#F5B301` | CTAs, active tabs, quality highlight |
| `onPrimary` | `#1A1400` | Text on gold |
| `primaryContainer` | `#C48A00` | Pressed/tonal gold |
| `secondary` (Teal) | `#2DD4BF` | Secondary chips, links |
| `tertiary` (Violet) | `#A78BFA` | Accents, genre tags |
| `background` (Ink) | `#0B0B10` | App canvas |
| `surfaceContainerLow` | `#14141C` | Cards |
| `surfaceContainer` | `#1C1C26` | Bottom bar, fields |
| `surfaceContainerHigh` | `#22222E` | Skeleton, icon tiles |
| `onSurface` (Cream) | `#FFF8E7` | Primary text |
| `onSurfaceVariant` | `#C9C3B4` | Secondary text |
| `outline` | `#2C2C3A` | Hairlines, card borders |

### Light

Paper `#FAF7F2` canvas, `#7A5600` gold (contrast-safe on light), ink text
`#1B1A17`. Selected via Settings → Theme → Light.

### Semantic (theme-aware, `ExtendedColors`)

| Token | Dark | Meaning |
| --- | --- | --- |
| `swarmStrong` | `#4ADE80` | ≥ 50 seeders |
| `swarmFair` | `#FBBF24` | 10–49 seeders |
| `swarmWeak` | `#F87171` | 1–9 seeders |
| `swarmDead` | `#8B8779` | 0 seeders |
| `ratingBadge` | Gold | IMDb/TMDB pill |
| `qualityBadge` | `#262633` | Resolution/source pill |
| `heroScrim` | `#0B0B10 @ 88%` | Text legibility over art |

Colour is never the only signal: swarm health also prints numbers, and state
changes are announced through snackbars for TalkBack users.

---

## Typography

Platform font, tuned scale (`ui/theme/Type.kt`):

| Style | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| `displaySmall` | 30 / 36 | Bold | Hero title |
| `headlineMedium` | 24 / 30 | Bold | Detail title |
| `headlineSmall` | 20 / 26 | SemiBold | Section headers |
| `titleLarge` | 20 / 26 | SemiBold | App bar titles |
| `titleMedium` | 16 / 22 | SemiBold | List row titles |
| `titleSmall` | 14 / 20 | Medium | Poster captions |
| `bodyLarge` | 16 / 24 | Normal | Synopsis |
| `bodyMedium` | 14 / 21 | Normal | Dialog/body copy |
| `bodySmall` | 12 / 17 | Normal | Metadata |
| `labelLarge` | 14 / 18 | SemiBold | Buttons |
| `labelSmall` | 10 / 14 | Bold, 0.6 tracking | Badges |

Negative tracking on display sizes keeps long titles from sprawling.

---

## Shapes and spacing

- Poster cards `16 dp`, hero `24 dp`, release cards `18 dp`, badges `8 dp`,
  chips/filters fully rounded.
- 4 dp base grid; screen gutters 16 dp; card gaps 12 dp (grid) / 10 dp (lists).
- Edge-to-edge: `enableEdgeToEdge()` in the activity, `Scaffold` insets in each
  screen, transparent system bars with icon contrast driven by the theme.

---

## Component inventory (`ui/components/`)

| Component | Notes |
| --- | --- |
| `PosterImage` | `SubcomposeAsyncImage` with shimmer, artwork fallback, error icon, and the title rendered inside the placeholder |
| `MovieCard` | 2:3 poster, top-start quality chip, top-end rating pill, bottom-start seeder pill, press-scale animation, bookmark flag |
| `MovieRow` | Watchlist/search row: 64×96 thumbnail, rating, 2-line synopsis, optional trailing action |
| `HeroBanner` | Full-bleed backdrop, two-stage scrim, eyebrow pill, title, meta row, single CTA |
| `RatingBadge` / `QualityBadge` / `SwarmBadge` / `MetaPill` / `GenreTag` | Small, consistent metadata chips |
| `ReleaseCard` | Quality + size header, seeders/leechers row, health bar, metadata line, Open/Copy actions |
| `SkeletonBlock`, `PosterGridSkeleton`, `ListRowsSkeleton` | Pulsing placeholders that mirror the real layout |
| `EmptyState`, `ErrorState`, `NoResultsState`, `InlineNotice` | Consistent empty/error/offline surfaces |
| `Formatters` | Binary sizes, compact counts, ISO years, text cleanup, health ratio |

---

## Motion

| Interaction | Spec |
| --- | --- |
| Poster press | scale 1.0 → 0.97, spring, ~150 ms |
| Screen enter | fade 220 ms; detail slides up 12 % + fade |
| Bottom bar show/hide | slide 240 ms + fade |
| Skeletons | alpha 0.35 → 0.85, 900 ms, reverse repeat |
| Image load | Coil crossfade 220 ms |

Motion is decorative only: every animation has an instant end state, and nothing
blocks input.

---

## Accessibility checklist

- Poster cards announce “Title, year, rated X” via `contentDescription`.
- Every icon-only button has a content description from `strings.xml`.
- Minimum touch target 48 dp (`IconButton`, buttons at 44 dp height inside a
  48 dp slot).
- Text contrast: cream on ink ≈ 17:1, gold on ink ≈ 9:1, muted text ≥ 4.5:1.
- Snackbars are the only place errors surface, and they never auto-dismiss a
  required action without offering one (`Undo`, `Retry`).
- Dynamic colour (Android 12+) is opt-in so the brand palette stays available.

---

## Brand assets

`branding/generate_icons.py` draws the mark with Pillow and exports:

- legacy launcher PNGs (48–192 px) and round variants,
- adaptive foreground layers (108 dp grid, emblem inside the 66 dp safe zone),
- `app_icon_render.png` (512 px) and `feature_graphic.png` (1024×500) for the
  Play Store listing.

`res/drawable/ic_app_mark.xml` is the vector mark used in the top app bar;
`res/drawable/ic_splash_logo.xml` is the splash-screen variant. Rerun the script
after any palette change and commit the regenerated PNGs.
