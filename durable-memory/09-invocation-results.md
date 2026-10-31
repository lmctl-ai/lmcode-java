# Invocation-owned results — Java SDK slice 2

Consensus: skills-scripts/lmscript-track/designs/lmcode-return-and-reverse-templates-consensus.md.
Reference: C++ 3379c77, canonical std and fixtures copied at immutable 14868f8.

ResultSink is a normal host object, attached to Registry with setResultSink.
It is not stored in StdCollections.Arena or Context region state. Both run and
call can clear their standard arenas without touching the sink. Hosts reset
before a new logical invocation and retain across selected entry calls. Concurrent
invocations need distinct sinks and the existing registry execution discipline.

The 24 canonical signatures are installed by Stdlib.register/default Registry.
All accepted types are copied into owned JSON values. Json uses retained raw
numeric tokens plus a bounded decimal classifier before serialization; never
use the schema/manifest Json.parse double representation for this conversion.
Errors are RUN_HOST with the consensus prefix, JSON Pointer and fixed reason.
Failed writes preserve prior data. Field writes require object/null; insertion
order and overwrite position are preserved. Arbitrary handles are not stringified.

Reports.resultFrame returns LF UTF-8 bytes. The stream helper writes those bytes
and surfaces IOException, without printing by itself or closing the caller's
stream. Emit after successful dispatch and application validation only. There is
no automatic reporter modification: sample adoption belongs to the adopter slice.
The test host ReturnConformanceRunner demonstrates the actual success/error split.

Do not reuse Formatting.formatG15: Java's consensus formatter is Double.toString.
The canonical sum fixture's payload is exactly 0.30000000000000004. Java's legal
exponent/minimum-subnormal spelling need not match C++ to_chars outside the shared
byte fixtures; tests check exact binary64 round trips and the required sum bytes.

ResultSinkChecks is also reachable from the native SelfTest. ReturnMutationChecks
runs real javac production copies on the JVM, checking both failure exit and the
specific live assertion. The return fixture family is pinned and executed on JVM
and native; deliberate host resets, early frames and legacy rounding must be caught.

Canonical std/docs/version and fixture bytes are imported, not hand-regenerated.
The additional return-vectors lock carries the same provenance format as the
existing vector locks. PinVerifier also enforces exact standard signature parity.
