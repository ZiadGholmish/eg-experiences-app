# Design language

The source of truth for this app's visual language is **the design system on
claude.ai/design**. The tokens below are checked in so the code has something
concrete to compile against, and so nobody has to re-derive them from a 3.3 MB
artifact bundle.

They are **provisional**: where the synced design system disagrees, it wins.

## Where the tokens came from

`../be/docs/plan/implementation-plan.md` cites a design canvas — the link is in
that plan, in the private `eg-experiences-be` repo — with 8 mobile artboards at 390px:
home with category filters, map view, trip detail, date + party, checkout with a
hold timer, confirmation, and a host dashboard. The values in
`core/designsystem/src/commonMain/kotlin/.../theme/` were read out of that
canvas's markup.

One trap for anyone re-deriving them: the canvas bundle embeds a **map widget**
whose own chrome uses a different teal (`#14606A`), Roboto, and 3–5px radii.
That is Leaflet's stylesheet, not this product's design. Read the root canvas
template, not the nested map page.

## Syncing from claude.ai/design

1. Run `/design-login` in Claude Code once, to grant design-system access.
2. Run `/design-sync` and pick the Burlus / Egypt Experiences design system.
3. The sync writes per-component specs; reconcile them against
   `core/designsystem` one component at a time, and update this file's tables.

Until step 1 is done, the tables below are what the app ships.

## Colour

| Token | Hex | Used for |
|---|---|---|
| `EgTeal` | `#0E7C86` | Primary action, price, active chip, hero |
| `EgTealTint` | `#EAF7F8` | Chip and icon-pill backgrounds |
| `EgCoral` | `#FF6B4A` | Urgency: hold timer, "2 seats left" |
| `EgCoralTint` | `#FFF0E9` | Badge background for the above |
| `EgCoralDeep` | `#A93C1C` | Text on `EgCoralTint` |
| `EgInk` | `#12232A` | Primary text |
| `EgInkMuted` | `#41585F` | Secondary text |
| `EgInkSubtle` | `#5C6F77` | Body copy |
| `EgInkFaint` | `#8B9B9F` | Metadata, disabled |
| `EgSurface` | `#FFFFFF` | Cards, sheets |
| `EgSurfaceMuted` | `#F7FAFA` | Section backgrounds |
| `EgSurfaceSunken` | `#F2F6F6` | Image placeholders |
| `EgStroke` | `#DFE9EA` | Dividers, unselected borders |
| `EgSuccess` / `EgSuccessTint` | `#1A6B4E` / `#E5F6EF` | Confirmed, verified host |
| `EgWarning` / `EgWarningTint` | `#C98A0E` / `#FFF6E3` | Pending |
| `EgDanger` | `#D94F28` | Errors, cancellation |

Light only. The canvas has no dark artboards, and a guessed dark palette is
worse than none.

## Type

**Manrope** for Latin, **IBM Plex Sans Arabic** as the fallback in the same
`FontFamily`. Arabic is the default locale, so the Arabic cut is the common
case, not the exception.

Static weight cuts, not the variable Manrope: minSdk is 24 and Android only
honours variable-font weight axes from API 26.

The canvas runs 10.5–30sp, with **weight** carrying most of the hierarchy — 500
Medium for body, 700 Bold for labels, 800 ExtraBold for titles and prices. Named
roles are in `EgTypography`; Material's slots are mapped to the nearest step so
`MaterialTheme.typography.*` stays usable.

## Shape and spacing

Two radii do almost all the work: **100dp pills** (every chip, badge, primary
button) and **20dp cards** (photo tiles, content blocks). Sheets are 24dp on the
top corners only.

The screen gutter is **18dp** — every 390px artboard pads its content by 18.

## Rules

- No `.dp`, `Color(0x…)` or `.sp` literals in a feature module. Everything comes
  from `EgTheme.spacings`, `EgTheme.shapes`, `EgTheme.typography` and the named
  colours.
- Lay out with `start`/`end`, never `left`/`right`. Arabic is RTL and is the
  default, so a hardcoded side is a bug for most users, not an edge case.
