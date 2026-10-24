# CI and release

Five native matrix targets: Ubuntu22.04 x64/arm64, macOS15 Intel, macOS14 ARM, Windows-latest x64. The requested macOS13 runner is retired; official notice https://github.blog/changelog/2025-09-19-github-actions-macos-13-runner-image-is-closing-down/. setup-graalvm installs CE21. Every job runs JVM and native conformance; Linux enforces the glibc-family NEEDED allowlist.

Main source CI https://github.com/lmctl-ai/lmcode-java/actions/runs/40138016505 passed all5 platforms. Tag releases publish five lmcode-java-<platform> greeter binaries plus SHA256SUMS. Native greeters embed their schema and complete notices; compiler install= URLs resolve the matching platform asset.
