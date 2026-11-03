#!/usr/bin/env bash
# Stamp the disposable CI checkout before Maven and native-image packaging.
set -euo pipefail
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
version=${1:-}
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo 'expected MAJOR.MINOR.PATCH' >&2; exit 2; }
python3 - "$version" <<'PY'
from pathlib import Path
import re
import sys

version = sys.argv[1]
root = Path.cwd()
(root / 'VERSION').write_text(version + '\n', encoding='utf-8')
pom_path = root / 'pom.xml'
pom = pom_path.read_text(encoding='utf-8')
pom, pom_count = re.subn(
    r'(<artifactId>lmcode-java</artifactId>\s*<version>)[^<]+(</version>)',
    rf'\g<1>{version}\g<2>', pom, count=1)
source_path = root / 'src/main/java/io/lmctl/lmcode/examples/Greeter.java'
source = source_path.read_text(encoding='utf-8')
source, source_count = re.subn(r'(VERSION\s*=\s*")[^"]+(";\s*)', rf'\g<1>{version}\g<2>', source, count=1)
if pom_count != 1 or source_count != 1:
    raise SystemExit('lmcode-java version markers changed; refusing an ambiguous stamp')
pom_path.write_text(pom, encoding='utf-8')
source_path.write_text(source, encoding='utf-8')
PY
