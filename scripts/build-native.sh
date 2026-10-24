#!/usr/bin/env bash
set -euo pipefail
timeout_cmd() { if [ -x /usr/bin/timeout ]; then /usr/bin/timeout "$@"; elif command -v gtimeout >/dev/null 2>&1; then gtimeout "$@"; else timeout "$@"; fi; }
if ! command -v native-image >/dev/null 2>&1; then echo "native-image is required" >&2; exit 2; fi
out=target/greeter
case "$(uname -s)" in MSYS*|MINGW*|CYGWIN*) out=target/greeter.exe;; esac
flags=(-H:+UnlockExperimentalVMOptions -J-Xmx4g -H:NumberOfThreads=2 --no-fallback -march=compatibility -H:+ReportExceptionStackTraces '-H:IncludeResources=greeter.schema.json|notices.txt' -cp target/classes)
if [[ "$(uname -s)" == Linux* ]]; then
  flags+=("-H:+StaticExecutableWithDynamicLibC" "-H:NativeLinkerOption=-static-libstdc++" "-H:NativeLinkerOption=-static-libgcc")
fi
timeout_cmd 1800 native-image "${flags[@]}" io.lmctl.lmcode.examples.Greeter "$out"
test -f "$out"
