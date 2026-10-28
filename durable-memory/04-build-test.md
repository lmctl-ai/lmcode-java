# Build and test

JDK21 and Maven3.6.3+. mvn -q verify executes pinned-tree/schema checks, six gallery-file checks, nine unit/regression groups, the rule-methods schema pin, 70 core vectors and 267 standard v3 vectors (181 canonical std signatures). Fixtures are copied unchanged from lmcode-cpp commit d608ba2f7eaf11e71fdd9a2104fcf90f277772aa; vectors.lock/stdlib-vectors.lock/std.lock record hashes. scripts/sync-vectors.sh restores locked revisions; --update advances both suites and standard-schema locks to the reference HEAD without regenerating bytecode; --verify checks the local copies after compile.

RUN_NATIVE=1 ./test.sh builds the Greeter via GraalVM CE21, then checks version, exact embedded schema/notices, hello bytecode, unit groups and both vector suites. Linux uses -H:+StaticExecutableWithDynamicLibC (the Java21 equivalent of --static-nolibc) so only glibc is dynamic. Windows builds via native-image.cmd/PowerShell and lets GraalVM append.exe. No fallback JVM images.

EntryChecks is included in JVM and native self-tests. It has independent wire fixtures for v5 refusal, entry metadata/layout, unconditional HALT depth, bounds, jumps and runtime source locations. The port was also built and executed locally with GraalVM CE25 (Java21 class target); CI retains its GraalVM CE21 matrix.
