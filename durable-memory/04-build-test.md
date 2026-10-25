# Build and test

JDK21 and Maven3.6.3+. mvn -q verify executes pinned-tree/schema checks, six gallery-file checks, eight unit/regression groups,51 core vectors and118 standard vectors. Fixtures are copied unchanged from lmcode-cpp commit53b9bdf851875ab904518e92347934b83874c706; vectors.lock/stdlib-vectors.lock/std.lock record hashes. scripts/sync-vectors.sh restores locked revisions; --update advances both suites and standard-schema locks to the reference HEAD without regenerating bytecode; --verify checks the local copies after compile.

RUN_NATIVE=1 ./test.sh builds the Greeter via GraalVM CE21, then checks version, exact embedded schema/notices, hello bytecode, unit groups and both vector suites. Linux uses -H:+StaticExecutableWithDynamicLibC (the Java21 equivalent of --static-nolibc) so only glibc is dynamic. Windows builds via native-image.cmd/PowerShell and lets GraalVM append.exe. No fallback JVM images.
