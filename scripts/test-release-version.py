#!/usr/bin/env python3
"""The committed Java SDK version floor plus CI run number is stable."""
from pathlib import Path
import re
import subprocess
import tempfile
import shutil

root = Path(__file__).resolve().parents[1]
current = (root / "VERSION").read_text(encoding="utf-8").strip()
match = re.fullmatch(r"(0\.1)\.(\d+)", current)
assert match, current
patch = int(match[2])

def derive(*args):
    return subprocess.check_output(["bash", root / "scripts/derive-release-version.sh", *args], text=True).strip()

assert derive("--diagnostic") == current
assert derive("1") == f"0.1.{patch + 1}"
assert derive("17") == f"0.1.{patch + 17}"
assert derive("17") == f"0.1.{patch + 17}"
with tempfile.TemporaryDirectory(prefix="lmcode-java-version-stamp-") as temp:
    work = Path(temp)
    (work / "scripts").mkdir()
    (work / "src/main/java/io/lmctl/lmcode/examples").mkdir(parents=True)
    for name in ("VERSION", "pom.xml"):
        shutil.copyfile(root / name, work / name)
    shutil.copyfile(root / "src/main/java/io/lmctl/lmcode/examples/Greeter.java", work / "src/main/java/io/lmctl/lmcode/examples/Greeter.java")
    shutil.copyfile(root / "scripts/stamp-version.sh", work / "scripts/stamp-version.sh")
    stamped = f"0.1.{patch + 17}"
    subprocess.run(["bash", work / "scripts/stamp-version.sh", stamped], check=True)
    assert (work / "VERSION").read_text(encoding="utf-8").strip() == stamped
    assert re.search(rf'<artifactId>lmcode-java</artifactId>\s*<version>{re.escape(stamped)}</version>', (work / "pom.xml").read_text())
    assert f'VERSION="{stamped}"' in (work / "src/main/java/io/lmctl/lmcode/examples/Greeter.java").read_text()
print("PASS lmcode-java automatic release version and Maven/JVM stamping")
