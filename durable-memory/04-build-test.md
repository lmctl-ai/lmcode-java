# Build and test

JDK21 and Maven3.6.3+. mvn -q verify checks immutable standard/schema pins, six gallery files, rule-method and template contracts, 410 core vectors, 268 standard vectors, nine return fixtures and 19 invocation-envelope goldens. The default registry has 206 canonical v3 signatures. Result and dry-callback checks run inside SelfTest/InvocationChecks and isolated production-copy mutation controls. The four vector locks and std.lock record immutable C++ provenance. scripts/sync-vectors.sh restores pinned corpora; --update explicitly advances their references without regenerating bytecode. Use --verify after compiling to validate hashes.

RUN_NATIVE=1 ./test.sh builds the Greeter via GraalVM CE21, then checks version, exact embedded schema/notices, hello bytecode, unit groups and both vector suites. Linux uses -H:+StaticExecutableWithDynamicLibC (the Java21 equivalent of --static-nolibc) so only glibc is dynamic. Windows builds via native-image.cmd/PowerShell and lets GraalVM append.exe. No fallback JVM images.

EntryChecks is included in JVM and native self-tests. It has independent wire fixtures for v5 refusal, entry metadata/layout, unconditional HALT depth, bounds, jumps and runtime source locations. The port was also built and executed locally with GraalVM CE25 (Java21 class target); CI retains its GraalVM CE21 matrix.


## Invocation-result gate update

Current immutable data reference: 9525cac86376d4dc7bcc3268fac695a7bfbde2ac.
`mvn -q verify` executes 410 core, 268 standard, nine return and 19 invocation
fixtures, typed result/ownership checks, 24-signature registration, retained
invocation/return mutations, and the dry sentinel/fallback/double-call/shared-dummy
controls. Existing standard/schema/bytecode formats remain v3/v2/v6. PinVerifier
validates all four vector locks and std.lock. Canonical source, .dslb and .expect
fixtures are copied unchanged through the repository sync script.
`LMCODE_CPP_DIR` plus `LMCODE_CPP_REF` lets sync-vectors.sh --update read an
immutable ref fetched into an OWN clone, without operating on a shared checkout.
The native test.lms lane calls --return-conformance after its standard fixtures.
