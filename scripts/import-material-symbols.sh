#!/usr/bin/env bash
# Imports Material Symbols Rounded (24px, weight 400, grade 0) as Compose Multiplatform vector
# drawables into core:designsystem. Re-run after adding a name to ICONS, then add the matching
# entry to BahrIcons.kt.
#
# Source: google/material-design-icons, symbols/android/<name>/materialsymbolsrounded/, pinned to
# COMMIT so every run produces the same files. Licence: Apache 2.0 (copy shipped in
# composeResources/files/licenses/).
#
# The upstream files are Android VectorDrawables. Compose resources skip aapt, so two Android-only
# references are rewritten, otherwise the icon throws the first time it is drawn:
#   android:fillColor="@android:color/white"  -> "#FF000000"   (Icon() tints it anyway)
#   android:tint="?attr/colorControlNormal"    -> removed
# android:autoMirrored="true" is kept exactly where Google set it.
#
# Output: ic_<name>.xml (outlined, FILL 0) and ic_<name>_filled.xml (FILL 1). When both variants
# are byte-identical upstream, only ic_<name>.xml is written.
#
# The output directory ends up holding exactly the icons in ICONS: every ic_*.xml is rebuilt in a
# staging directory and swapped in only after all downloads and checks pass, so an icon removed
# from the list is pruned and a failed run leaves the old files untouched.
# OUT can be overridden (OUT=/tmp/icons scripts/import-material-symbols.sh) to dry-run elsewhere.
set -euo pipefail

COMMIT="737e3324305806514d7909874fa1818ae1808232"
BASE="https://raw.githubusercontent.com/google/material-design-icons/${COMMIT}/symbols/android"
OUT="${OUT:-$(cd "$(dirname "$0")/.." && pwd)/core/designsystem/src/commonMain/composeResources/drawable}"

# One request must not hang the run (it did once, for >5 min); transient failures are retried.
CURL=(curl -sfL --max-time 30 --retry 3 --retry-all-errors --retry-delay 2)

# Every icon used by the prototype (docs/design/prototype/Burullus Color.dc.html) and HANDOFF.md.
# `place` is only a ligature alias in the icon font; the Symbols file is `location_on`.
ICONS=(
  add apps arrow_back arrow_forward backpack beach_access bedtime block calendar_month campaign
  chat check circle close credit_card directions_bus event event_busy event_seat event_upcoming
  favorite flutter_dash format_list_bulleted groups home info ios_share local_cafe location_on lock
  map more_vert notifications_active palette payments person pin_drop remove restaurant sailing
  schedule sell smartphone star storefront sunny timer verified visibility waves
)

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
STAGE="$TMP/out"
mkdir -p "$OUT" "$STAGE"

sanitize() {
  perl -0pe 's|android:fillColor="\@android:color/white"|android:fillColor="#FF000000"|g;
             s|\s*android:tint="\?attr/colorControlNormal"||g' "$1" > "$2"
}

for name in "${ICONS[@]}"; do
  "${CURL[@]}" -o "$TMP/$name.xml" "$BASE/$name/materialsymbolsrounded/${name}_24px.xml"
  "${CURL[@]}" -o "$TMP/${name}_fill1.xml" "$BASE/$name/materialsymbolsrounded/${name}_fill1_24px.xml"
  sanitize "$TMP/$name.xml" "$STAGE/ic_${name}.xml"
  if ! cmp -s "$TMP/$name.xml" "$TMP/${name}_fill1.xml"; then
    sanitize "$TMP/${name}_fill1.xml" "$STAGE/ic_${name}_filled.xml"
  fi
done

if grep -rlE '@android:|\?attr/|="(Round|Butt|Square|Miter|Bevel|EvenOdd|NonZero)"' "$STAGE"; then
  echo "error: Android-only reference or capitalised enum value left in the files above" >&2
  exit 1
fi

# Swap in: prune every icon drawable, then copy the fresh set. Other drawables are not touched.
find "$OUT" -maxdepth 1 -name 'ic_*.xml' -delete
cp "$STAGE"/ic_*.xml "$OUT"/
echo "Imported ${#ICONS[@]} icons into $OUT ($(find "$OUT" -maxdepth 1 -name 'ic_*.xml' | wc -l | tr -d ' ') files)"
