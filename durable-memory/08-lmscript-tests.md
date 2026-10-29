# lmscript tests and native samples

Install lmctl and lmscript together with `npm install -g @lmctl-ai/lmctl`.
Root `test.lms` drives SDK checks and the annotated `Score(int) -> int` example;
`test.sh` delegates to it. `RUN_NATIVE=1` retains the greeter's native protocol,
notices, self-test and both conformance suites. CI runs on pushes and PRs;
version tags retain the native SDK release path.

The Java/Kotlin/Scala samples vendor the canonical lmcode-cpp lmscript test
suite and use GraalVM Community 21 to build real executables on five targets.
Their main pushes publish only after all JVM and native checks pass. Native
Image includes the exact sample schema and generated version resources.
