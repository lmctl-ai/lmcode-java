#!/usr/bin/env bash
set -euo pipefail
timeout_cmd() { if [ -x /usr/bin/timeout ]; then /usr/bin/timeout "$@"; elif command -v gtimeout >/dev/null 2>&1; then gtimeout "$@"; else timeout "$@"; fi; }
timeout_cmd 1200 mvn -q verify
timeout_cmd 30 java -cp target/classes io.lmctl.lmcode.examples.Greeter --dsl examples/greeter/hello.dslb
if [[ "${RUN_NATIVE:-0}" == 1 ]]; then
  timeout_cmd 1800 scripts/build-native.sh
  native=target/greeter
  [[ -f "$native" ]] || native=target/greeter.exe
  test "$(timeout_cmd 30 "$native" --version)" = "lmcode-java 0.1.0"
  timeout_cmd 30 "$native" --dsl-schema --json > target/native-schema.json
  cmp src/main/resources/greeter.schema.json target/native-schema.json
  timeout_cmd 30 "$native" --notices > target/native-notices.txt
  cmp THIRD_PARTY_NOTICES target/native-notices.txt
  timeout_cmd 30 "$native" --dsl examples/greeter/hello.dslb
  timeout_cmd 300 "$native" --self-test
  timeout_cmd 600 "$native" --conformance conformance/vectors
  timeout_cmd 600 "$native" --conformance conformance/stdlib-vectors
fi
echo "Gallery source/images checked; regeneration requires optional lmscript and lmplot (examples/render-all.sh)."
echo "lmcode-java verification passed"
