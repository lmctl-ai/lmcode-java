# CI and release

Five native matrix targets: Ubuntu22.04 x64/arm64, macOS15 Intel, macOS14 ARM, Windows-latest x64. The requested macOS13 runner is retired; official notice https://github.blog/changelog/2025-09-19-github-actions-macos-13-runner-image-is-closing-down/. setup-graalvm installs CE21. Every job runs JVM and native conformance; Linux enforces the glibc-family NEEDED allowlist.

Main source CI https://github.com/lmctl-ai/lmcode-java/actions/runs/40138016505 passed all5 platforms. Tag releases publish five lmcode-java-<platform> greeter binaries plus SHA256SUMS. Native greeters embed their schema and complete notices; compiler install= URLs resolve the matching platform asset.

Published [v0.1.0](https://github.com/lmctl-ai/lmcode-java/releases/tag/v0.1.0).
[Tagged CI40138341440](https://github.com/lmctl-ai/lmcode-java/actions/runs/40138341440)
passed all five platforms and publication. Release assets are the five native greeters
plus SHA256SUMS; every downloaded checksum verified. Published Linux x64 is19,073,144B;
ARM64 is18,561,328B. Both Linux ELF files have only glibc-family dependencies and
require at most GLIBC2.34 symbols; the supported baseline remains glibc2.35.
The released x64 independently passes all six unit groups,51 core and77 std vectors,
and its embedded notices match source byte-for-byte.

All six README raw URLs passed lmctl autorun from independent empty directories and
fresh caches, with no lmcode-java on PATH and GITHUB_TOKEN obtained from gh auth token.
Each installed version0.1.0, matched released x64 SHA256
07f431543f9892d79a098ab3deb22c7e167aacf4d84feaf934862b17ee3df14b,
passed its checks and wrote a valid PNG. Proof: autorun-v010.json and release-v010.json.
