#!/usr/bin/env bash
set -euo pipefail
timeout_cmd() { if [ -x /usr/bin/timeout ]; then /usr/bin/timeout "$@"; elif command -v gtimeout >/dev/null 2>&1; then gtimeout "$@"; else timeout "$@"; fi; }
timeout_cmd 1200 mvn -q verify
if [[ "${RUN_NATIVE:-0}" == 1 ]]; then
  timeout_cmd 1800 scripts/build-native.sh
  native=target/greeter
  [[ -f "$native" ]] || native=target/greeter.exe
  timeout_cmd 300 "$native" --self-test
  timeout_cmd 600 "$native" --conformance conformance/vectors
  timeout_cmd 600 "$native" --conformance conformance/stdlib-vectors
fi
echo "lmcode-java verification passed"
