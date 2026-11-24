#!/usr/bin/env bash
# Syncs core's crypto icon library into the app's assets:
#
#   <spectra>/icons/crypto/*.svg → app/src/main/assets/crypto/
#
# The library is core's, in the spectra checkout SPECTRA names (absolute or
# relative to this repository's root), where it is normalized and checked
# against the catalog's artwork names. The app draws each SVG as it ships, by
# its artwork name, so file names stay as they are.
#
# Copies a new or changed SVG, removes one whose source is gone, and refuses
# a source with no SVGs: that is a wrong SPECTRA, not an empty library, and
# syncing it would remove every icon.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${SPECTRA:-}" ]]; then
  echo "error: the crypto icons are core's: set SPECTRA to a spectra checkout" >&2
  exit 2
fi
SOURCE="$(cd "$REPO_ROOT" && cd "$SPECTRA" && pwd)/icons/crypto"
if ! compgen -G "$SOURCE/*.svg" >/dev/null; then
  echo "error: no icons in $SOURCE: SPECTRA must name a spectra checkout" >&2
  exit 2
fi
DEST="$REPO_ROOT/app/src/main/assets/crypto"
mkdir -p "$DEST"

copied=0
removed=0
for svg in "$SOURCE"/*.svg; do
  name="$(basename "$svg")"
  if ! cmp -s "$svg" "$DEST/$name"; then
    cp "$svg" "$DEST/$name"
    copied=$((copied + 1))
  fi
done
for svg in "$DEST"/*.svg; do
  [[ -e "$svg" ]] || continue
  if [[ ! -e "$SOURCE/$(basename "$svg")" ]]; then
    rm "$svg"
    removed=$((removed + 1))
  fi
done
echo "crypto icons: ${copied} copied, ${removed} removed ($(ls "$DEST" | wc -l | tr -d ' ') in assets)"
