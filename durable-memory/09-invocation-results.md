# Invocation results, envelopes and dry callbacks — Java

Contract: skills-scripts/lmscript-track/designs/lmcode-exec-and-result-envelope-amendment.md.
Canonical C++ reader/collector/contract and offline goldens: H0 main 9525cac.
This supersedes the former sink-only successful payload framing.

`InvocationResults.fromCommand(List<String>)` consults the process environment
without mutating it. Explicit manifest-path and raw-metadata constructors support
native embedding/tests; no public host manifest flag or script file getter exists.
Strict UTF-8/BOM/duplicate-key/raw-number validation precedes dispatch. Metadata
requires exact root/part keys, version 1, contiguous indices, unique absolute files
and a complete registration permutation. Descriptor order supplies canonical slot
indices even when parsing reverses argv. Standalone CLI duplicates are separate
occurrences; one-slot builder shape requires its descriptor.

## Dry callback mode (H3)

Registrations retain one real `Callback` plus an optional `Registry.DryCallback`.
Register a preview with `function(..., real, Registry.dry(preview))` or the
analogous method overload. The helper stores the supplied callback and does not
run it. Without a dry slot, dispatch skips the real callback and creates a fresh
dummy from the declared return type. `void` stays stack-neutral, booleans default
true, numeric types default zero, strings are empty, typed lists/maps are new
run-owned collections, and unknown/object types are internal provenance-marked
dry nulls.

`Context.dry()` is invocation-local. Hosts set it from `InvocationResults.dry()`
before running body and entries. Dry-null is not a live host handle and cannot be
passed to a function or entry argument; methods without a dry slot can skip it
without dereference. An overridden method requires a real receiver. Result sink
writes and run-owned std collection operations execute normally so `$return`
contents are the actual dry-path values.

Version 1 manifests require boolean `dry`. Collectors retain the mode and writers
append `dry:true` after existing success/failure keys only for dry invocations.
Missing or nonboolean mode is a CLI error with no guessed marker. A failure before
collector construction carries the marker only if a valid dry value was parsed.
Standalone commands default to real; embedded hosts may use
`InvocationResults.fromCommand(files, true)`. File reads and existence checks use
their declared dry-safe callbacks; file writes/appends are skipped with `void`
dummies. Other pure, run-owned, result-sink and testing callbacks have explicit
dry slots. This callback policy does not sandbox arbitrary host code.

`select(index, registry, contract)` allocates a fresh sink per slot and permits
active reselection without reset or contract changes. `complete(index)` validates,
returns an owned snapshot internally and seals the sink. A null result is a real
completed result; it is distinct from missing completion. Errors during completion
latch the run/slot association before rethrowing. Sealed writes/reset/reselection
and repeated completion fail. Never share a root between slots or clear it on arena
exit. The registry's existing per-run guard and host serialization still apply;
this collector is not a scheduler and does not wait for host tasks.

`fail(indexOrNull, phase, error)` validates and copies the first observed failure,
including its mutable location fields. Subsequent failures cannot overwrite it,
even if their arguments are invalid. Do not start a new slot after failure; drain
already-selected work. Global app failure discards all snapshots. The writer emits
one compact add-order success or flat failure envelope, returns 0/2/3/4, flushes LF
UTF-8 bytes, and does not close streams. It marks publication before writing and
cannot retry after partial output, IOException or a swallowed PrintStream error.
The pre-collector helper is `Reports.failureFrame`; callers return immediately.
The old sink-only frame/report-success functions were removed, without aliases.

`ResultContract.fromSchema(byte[])` reads authoritative schema 2 metadata; the
manifest cannot supply a contract. The optional result type has 12 approved names
and nullable defaults false; null forbids the nullable key. Unknown keys/types are
LOAD_BAD_SCHEMA. Initial/reset null is unfinished construction, but explicit null
and completion obey root nullability. Typed collection members remain non-null;
Json permits nested null and, when nonnullable, a non-null root of any JSON shape.
Known widening preserves numeric representation. Field candidates are validated
before assignment, so a refused write leaves the previous root intact.

The 24 return signatures, bytecode version, VM/HALT values and owned copies remain.
Raw numeric tokens are classified before rounded values hide unsafe integers or
nonzero underflow. Frame floats use Double.toString, never Formatting.formatG15;
0.1 + 0.2 remains 0.30000000000000004. The strict metadata parser reuses the existing
JSON node reader and result validator rather than a permissive double-only reader.
Java's launcher already decodes raw OS argv into Unicode; the API rejects invalid
UTF-16 strings before filesystem access and strict byte-based manifest UTF-8.
It cannot recover original invalid OS argv bytes from a replacement character.

The sync script and PinVerifier own all four corpus pins. Maven verify always runs
compiled missing-seal, reused-sink, wrong-part, omitted-flush, dry callback and
fresh-dummy faults; a compile failure/crash is not a caught fault. The golden
harness executes real programs in one process, including reverse order, duplicate programs, shared host
counter, null/nested/list results, first failure and app failure. Native test.lms
runs those same golden bytes via the hidden Greeter fixture entrypoint. Fixtures
are generated/pinned from canonical source, never inferred from runtime stdout.
External Java shop sample adoption is a separate R7 change.

H3 adds shared byte goldens for dry single success, dry override failure and
builder dry failure. `DryChecks` proves the real callback leaves a file sentinel
absent, preview executes once, real mode remains unchanged, dummies are fresh,
and std dry policies are deliberate. The production-copy controls catch fallback
to real, double-call and shared-mutable-dummy faults.
