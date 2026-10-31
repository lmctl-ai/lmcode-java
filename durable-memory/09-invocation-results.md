# Invocation-owned results — Java SDK slice 2

Consensus: skills-scripts/lmscript-track/designs/lmcode-return-and-reverse-templates-consensus.md.
Reference: C++ result slice 3379c77, stream refinement 208650f; canonical std
and fixture bytes finally synchronized at b8615eda9fc56cd19eb5ffef62cd05e0d78cd977.

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

Reports.resultFrame returns LF UTF-8 bytes. The stream helper writes and flushes those bytes
and surfaces IOException, including PrintStream trouble flags, without printing
by itself or closing the caller's stream. Emit after successful dispatch and application validation only. There is
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

C++ follow-up 208650f requires explicit flush before successful framing. Java
controls first failed for deferred OutputStream and swallowed PrintStream errors;
the helper now surfaces both. The reference std/return fixture bytes are unchanged
through 1033429. The initial result port used 73 core cases. Concurrent Java commit ac0579e
advanced that family to the 397-case b8615ed core matrix without runtime changes.
After rebase, JVM and native both passed the complete expanded family; all data
locks now use that same immutable reference.

Serialize the complete body-plus-selected-entries sequence when reusing a registry.
The existing active guard protects individual run/call operations, not gaps between
them. Aliased registry references are not separate invocations; parallel hosts use
independent registry/context/sink triplets. Do not rebind a sink midway through an
invocation. Use an LF-preserving output stream for terminal protocol bytes.

The hidden Greeter --return-fixture test entry point runs the reference host and
writes its actual terminal frame to stdout. test.lms compares native sum/null
bytes and asserts empty stdout with exit 3/4 for entry/application failure. This
is test plumbing, not adoption of the ordinary Greeter `--dsl` reporter.
