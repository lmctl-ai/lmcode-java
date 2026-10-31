# Build and test

JDK21 and Maven3.6.3+. mvn -q verify executes pinned-tree/schema checks, six gallery-file checks, nine unit/regression groups, the rule-methods schema pin, 70 core vectors and 267 standard v3 vectors (181 canonical std signatures). Fixtures are copied unchanged from lmcode-cpp commit d608ba2f7eaf11e71fdd9a2104fcf90f277772aa; vectors.lock/stdlib-vectors.lock/std.lock record hashes. scripts/sync-vectors.sh restores locked revisions; --update advances both suites and standard-schema locks to the reference HEAD without regenerating bytecode; --verify checks the local copies after compile.

RUN_NATIVE=1 ./test.sh builds the Greeter via GraalVM CE21, then checks version, exact embedded schema/notices, hello bytecode, unit groups and both vector suites. Linux uses -H:+StaticExecutableWithDynamicLibC (the Java21 equivalent of --static-nolibc) so only glibc is dynamic. Windows builds via native-image.cmd/PowerShell and lets GraalVM append.exe. No fallback JVM images.

EntryChecks is included in JVM and native self-tests. It has independent wire fixtures for v5 refusal, entry metadata/layout, unconditional HALT depth, bounds, jumps and runtime source locations. The port was also built and executed locally with GraalVM CE25 (Java21 class target); CI retains its GraalVM CE21 matrix.


## Invocation-result gate update

Current immutable reference: 14868f84188abaf46d094e61abd291013d9bae1e.
`mvn -q verify` executes 73 core, 268 standard and nine frame fixtures, 13
result ownership/numeric groups, 24-signature registration, seven production-copy
mutants and three fault-host framing controls. Existing standard/schema/bytecode
formats remain v3/v2/v6. PinVerifier validates return-vectors.lock as well as the
original locks. Canonical source, .dslb, .expect and docs are copied unchanged.
`LMCODE_CPP_DIR` plus `LMCODE_CPP_REF` lets sync-vectors.sh --update read an
immutable ref fetched into an OWN clone, without operating on a shared checkout.
The native test.lms lane calls --return-conformance after its standard fixtures.
