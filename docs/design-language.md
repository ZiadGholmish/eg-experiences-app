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
