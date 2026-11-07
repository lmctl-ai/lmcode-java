#!/usr/bin/env bash
# Copy the shared REST conformance suite (the opt-in `rest` std module) from the spec repository and pin it.
#   scripts/sync-rest.sh            copy $LMCODE_SPEC_DIR/tests/rest-suite (default ../lmcode) and write rest-suite.lock
#   scripts/sync-rest.sh --verify   check conformance/rest-suite against rest-suite.lock
set -euo pipefail
cd "$(dirname "$0")/.."
case "${1:-}" in
  --verify) exec java -cp target/classes io.lmctl.lmcode.rest.RestSuitePin --verify ;;
  '') ;;
  *) echo 'usage: sync-rest.sh [--verify]' >&2; exit 2 ;;
esac
spec="${LMCODE_SPEC_DIR:-../lmcode}"
commit="$(git -C "$spec" rev-parse HEAD)"
mvn -q compile
rm -rf conformance/rest-suite
mkdir -p conformance/rest-suite
cp -R "$spec/tests/rest-suite/." conformance/rest-suite/
chmod -R u+rw conformance/rest-suite
java -cp target/classes io.lmctl.lmcode.rest.RestSuitePin --write-lock "$commit"
echo "synced REST suite from lmcode $commit"
