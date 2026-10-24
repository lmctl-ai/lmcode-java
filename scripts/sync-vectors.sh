#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" = --verify ]; then exec java -cp target/classes io.lmctl.lmcode.PinVerifier; fi
source="${LMCODE_CPP_DIR:-../lmcode-cpp}"
for suite in vectors stdlib-vectors; do
  pin="$(sed -n 's/^commit=//p' "$suite.lock")"
  mkdir -p "conformance/$suite"
  timeout 60 git -C "$source" archive "$pin" "conformance/$suite" | tar -xf -
done
printf 'Restored locked vectors; run mvn -q verify to validate hashes.\n'
