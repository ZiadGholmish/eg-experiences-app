# Design language

The source of truth is the **Bahr design system handoff**, vendored in the
workspace at `../docs/design/` (paths below are relative to `mobile-app/`):

- `../docs/design/design-system/tokens.json` — **the single source of truth** for
  every value (palette, colour roles, type scale, shape, elevation, spacing, motion).
- `../docs/design/design-system/tokens.css` — the same tokens for the web, plus the
  values of the two alternate themes (`dusk`, `highcontrast`), which `tokens.json`
  only names.
- `../docs/design/compose-reference/` — the Kotlin port this module was built from.
- `../docs/design/HANDOFF.md` — screen-by-screen specs and the token tables;
  `../docs/design/prototype/Burullus Color.dc.html` — the prototype (EN + AR copy,
  states).

`core:designsystem` (package `eg.bahr.core.designsystem`) is that port. When a
value changes, change `tokens.json` first, then `tokens.css`, then the Kotlin file
in the table below. If the Kotlin and `tokens.json` ever disagree, `tokens.json`
wins.

## Token mapping

| tokens.json | Compose | File |
|---|---|---|
| `ref.palette.*` | `BahrPalette.*` (never used by screens) | `theme/BahrColors.kt` |
| `sys.color.primary / secondary / tertiary / error` (+ `on*`, `*Container`) | `MaterialTheme.colorScheme.*` | `theme/BahrColors.kt` |
| `surface / surfaceLowest / surfaceLow / surfaceContainer / surfaceDim` | `colorScheme.surface / surfaceContainerLowest / surfaceContainerLow / surfaceContainer / surfaceDim` | `theme/BahrColors.kt` |
| `onSurface / onSurfaceVariant / outline / outlineVariant` | `colorScheme.*` | `theme/BahrColors.kt` |
| `primaryDim, primaryBright, primaryDeep, secondaryDim, tertiaryHover, quaternary*, success*, onSurfaceSecondary, onSurfaceDisabled, track, surfaceTranslucent`, scrims | `BahrTheme.colors.*` | `theme/BahrColors.kt` |
| `typescale.display` | `typography.displaySmall` / `BahrTheme.type.display` | `theme/BahrType.kt` |
| `typescale.headline / titleLarge / title` | `typography.headlineMedium / titleLarge / titleMedium` | `theme/BahrType.kt` |
| `typescale.body / bodySmall` | `typography.bodyLarge / bodyMedium` | `theme/BahrType.kt` |
| `typescale.label / labelSmall` | `typography.labelLarge / labelMedium` | `theme/BahrType.kt` |
| `typescale.overline` | `BahrTheme.type.overline` + `Overline()` | `theme/BahrType.kt` |
| price (product style) | `BahrTheme.type.price` | `theme/BahrType.kt` |
| `ref.typeface.plain / arabic` | Manrope / IBM Plex Sans Arabic, chosen by `BahrLocale` | `theme/BahrFonts.kt` |
| `shape.*` | `BahrTheme.shapes.*` (M3 `Shapes` gets extraSmall–extraLarge) | `theme/BahrShapeElevationMotion.kt` |
| `elevation.*` | `Modifier.bahrShadow(BahrElevation.X, shape, BahrTheme.colors)` | `theme/BahrShapeElevationMotion.kt` |
| `spacing` (4dp base, 18dp gutter) | `BahrSpacing.xs…xxl`, `BahrSpacing.gutter` | `theme/BahrShapeElevationMotion.kt` |
| `motion` | `BahrMotion` | `theme/BahrShapeElevationMotion.kt` |
| `[data-theme=dusk / highcontrast]` | `BahrTheme(theme = BahrThemeName.Dusk / HighContrast)` | `theme/BahrColors.kt` |
| `meta.currency`, `meta.timeFormat` | `BahrFormat` (EGP, 24h, Western digits) | `format/BahrFormat.kt` |

`BahrTheme(locale = …)` is set once at the app root from the stored language. It
picks the font, applies the Arabic type rules (one weight heavier at display
sizes, looser leading, no tracking, no uppercase) and sets the layout direction.

## Components

From the handoff (`../docs/design/compose-reference/components/BahrComponents.kt`): `BahrPrimaryButton`,
`BahrFilterChip`, `SeatBadge` (copy now from `core:localization`), `HoldCountdown`, `StickyActionBar`, `ImageGround`,
`Overline`, `BahrCard`.

Kept from the provisional set and rebuilt on Bahr tokens, because the handoff has
no equivalent: `BahrBadge` (tone-driven status pill) and `BahrLoadingView` /
`BahrErrorView` / `BahrEmptyView`.

New components use `interface X { @Immutable data class Props(…) }` +
`@Composable fun X(modifier, props)`, and enter the design system only once a
second caller exists.

## Fonts and icons

All assets live in `core/designsystem/src/commonMain/composeResources/`. Its `Res` class is
internal, so features reach them only through `BahrTheme` (fonts) and `BahrIcons` (icons). The
licence texts ship in the app under `files/licenses/`.

**Fonts** (`font/`, SIL OFL 1.1, static cuts because Android honours variable weight axes only
from API 26). Each file is byte-identical to the upstream file (sha256 checked 2026-10-08):

| Family | Weights | Source |
|---|---|---|
| Manrope 4.505 | 400 500 600 700 800 | `googlefonts/manrope` @ `468c0dbe38efa331b80bfe9448256abe27be44c3`, `fonts/ttf/manrope-<weight>.ttf` |
| IBM Plex Sans Arabic 1.101 | 400 500 600 700 | `google/fonts` @ `5e8a3ba899557829a76cfdac30fa512bda91d7ca`, `ofl/ibmplexsansarabic/IBMPlexSansArabic-<Weight>.ttf` |

| File | sha256 |
|---|---|
| `manrope_regular.ttf` | `d23d6eedf51dd495138aa1a83588027d4a63549cdfcc2fa5e0d8e84e09cf4cb1` |
| `manrope_medium.ttf` | `502ee33a6e140ad6ef10ef8b762a8e5cf01f33316f318800795725f4cfd539d9` |
| `manrope_semibold.ttf` | `f43edb46a08e1538413ea2383ef01ed93cdab191a2c57d2489d9590985858cf7` |
| `manrope_bold.ttf` | `484d10f0683e9e9f588de978f70cd3ad17cb3522bc0ca6efc4491c83429d323c` |
| `manrope_extrabold.ttf` | `c5c0faa3c3a1b1a184f6bb2e405931bdbf51d21c72d8162309118e91304988e0` |
| `ibm_plex_sans_arabic_regular.ttf` | `6f611412270a132bbac838da9259d4c68569b4175f3b3b8fa3fa36a30b56dab9` |
| `ibm_plex_sans_arabic_medium.ttf` | `b8363ab9f733dfa4f8e96b8b2102c24b5cf4110fb96d1d3d9a9412f6fb49cf74` |
| `ibm_plex_sans_arabic_semibold.ttf` | `597bd5502e5997be4414e4c9c88834b30ff3784250c84f20bce2b20e53ebd467` |
| `ibm_plex_sans_arabic_bold.ttf` | `691e0c891a38637ae6bbdb69700f8042cb0724a137bee615068ffdb92244f61f` |

Plex Arabic stops at 700, so `BahrFonts.kt` maps ExtraBold and Black to the Bold cut. The Arabic
rule "one weight heavier at display sizes" therefore has no visible effect: every style it bumps
already asks for 700 or more. The app ships whole fonts. Subsetting is a web concern only
(HANDOFF "Web performance").

**Icons** (`drawable/ic_<name>.xml`, `ic_<name>_filled.xml`, Apache 2.0): Material Symbols
Rounded 24px, weight 400, grade 0, from `google/material-design-icons` @
`737e3324305806514d7909874fa1818ae1808232`, `symbols/android/<name>/materialsymbolsrounded/`.
`scripts/import-material-symbols.sh` fetches them and makes them Compose-safe. It replaces the
Android-only `@android:color/white` fill and removes the `?attr/` tint, because both throw at draw
time in Compose resources. It writes one file when the outlined and filled forms are identical.
The list is every icon in the prototype and HANDOFF. `place` is the icon font's alias for
`location_on` (`BahrIcons.Place`). The prototype's `ios_share` is used rather than `share`.

Directional icons mirror by themselves in RTL (`android:autoMirrored`, set by Google on
`arrow_back`, `arrow_forward`, `chat`, `event_upcoming` and `format_list_bulleted`). Use
`BahrIcons.ArrowBack` for back in both languages. Do not copy the prototype's per-language swap
of `arrow_back` and `arrow_forward`: it exists only because a web icon font cannot mirror.

## Rules

- **No literals in feature code.** No `Color(0x…)`, raw `.dp`/`.sp`, or
  `FontFamily` in `feature/*` or `composeApp`. Screens read `MaterialTheme.*`,
  `BahrTheme.*`, `BahrSpacing` and `BahrMotion` only. Check:
  `grep -rnE --exclude-dir=build "Color\(0x|[^a-zA-Z_][0-9]+(\.[0-9]+)?\.dp\b|FontFamily\(" feature/ composeApp/src`.
- **Coral (`tertiary`) is the action colour and nothing else.** Only
  `BahrPrimaryButton`, and the hold / sold-out notices on `tertiaryContainer`.
- **Arabic is its own layout.** Use `start`/`end` and `Arrangement`, never
  `left`/`right`. Times and phone numbers stay LTR inside RTL.
- **Western digits, 24h, EGP** in both languages, through `BahrFormat`. Never a
  platform formatter with the `ar` locale.
- **Images never block.** `ImageGround` paints `surfaceDim` first; title, price,
  duration and CTA render without waiting on images.
