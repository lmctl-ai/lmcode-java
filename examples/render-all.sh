#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$root/docs/images"
for name in byte_strings ordered_map rounding_wrap multilingual_greetings file_pipeline standard_catalog; do
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT HUP INT TERM
  (cd "$work" && timeout 300 lmscript "$root/examples/$name.lms")
  test -s "$work/images/$name.png"
  cp "$work/images/$name.png" "$root/docs/images/$name.png"
  rm -rf "$work"
  trap - EXIT HUP INT TERM
done
