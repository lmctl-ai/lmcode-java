# lmcode-java durable memory

## 2026-10-24 release baseline

lmcode-java is a Java 21 SDK with no runtime dependencies beyond the JDK. Its public API is rooted at `io.lmctl.lmcode`; the default `Registry` installs `Stdlib`, while `Registry(false)` is empty. `VmString` is immutable raw bytes, so standard string functions operate on bytes rather than Java UTF-16 code units.

The standard catalog enforces 64 MiB string result limits, a 2²⁷ glob-work limit, 65,536 collection objects per run, and Context defaults of 4 GiB memory and 10 million instructions. Collection state is reset through the Registry run lifecycle.

Linux release builds are dynamic glibc only, with glibc 2.35 as the minimum. Native builds may statically link libstdc++ and libgcc, but never glibc; musl and alternate libc support are explicitly out of scope. The supported CI matrix is Ubuntu 22.04 x64/arm64, macOS 15 Intel, macOS 14 arm64, and Windows. macOS 13 was retired by the hosted-runner provider.

JVM and native conformance must be reported separately. Do not claim native availability on a platform until its GraalVM Native Image build and conformance run have passed. Keep shared conformance vectors byte-for-byte aligned with the current lmcode-cpp vector set.

## 2026-10-28 rule entry table

DSLB v6 only; R49 supersedes the v5 format baseline. See [entry table](06-v6-entry-table.md). Body opcodes and semantics are unchanged.
