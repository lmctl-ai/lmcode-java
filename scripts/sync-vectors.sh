#!/usr/bin/env bash
# Restore immutable pins by default, or explicitly advance both suites and std with --update.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" = --verify ]; then exec java -cp target/classes io.lmctl.lmcode.PinVerifier; fi
source="${LMCODE_CPP_DIR:-../lmcode-cpp}"
case "${1:-}" in ''|--update) ;; *) echo 'usage: sync-vectors.sh [--verify|--update]' >&2; exit 2;; esac
if [ "${1:-}" = --update ]; then
  pin="$(timeout 20 git -C "$source" rev-parse HEAD)"
  timeout 120 mvn -q compile
fi
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
for suite in vectors stdlib-vectors; do
  if [ "${1:-}" != --update ]; then pin="$(sed -n 's/^commit=//p' "$suite.lock")"; fi
  timeout 60 git -C "$source" archive "$pin" "conformance/$suite" | tar -xf - -C "$scratch"
  rm -rf "conformance/$suite"
  mkdir -p "conformance/$suite"
  cp -R "$scratch/conformance/$suite/." "conformance/$suite/"
done
if [ "${1:-}" = --update ]; then
  timeout 60 git -C "$source" show "$pin:std/lmcode-std.schema.json" > std/lmcode-std.schema.json
  timeout 30 java -cp target/classes io.lmctl.lmcode.PinVerifier --write-locks "$pin"
fi
printf 'Restored locked vectors; run mvn -q verify to validate hashes.\n'
