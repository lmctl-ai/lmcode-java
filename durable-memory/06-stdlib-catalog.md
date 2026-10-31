# Standard library catalog (v1–v3)

Canonical `std/lmcode-std.schema.json` defines 206 built-in signatures installed by default in `new Registry()` (`Stdlib.register`).

## v1 baseline (72 signatures)
Byte-oriented `str_*` helpers, `math_*` overloads (`int` and `float`), regular-file I/O (`file_*`), and run-bound `StrList`, `IntList`, `FloatList`, and `StrMap` collections.

## v2 containers and JSON (171 signatures)
Adds 99 signatures for `IntMap`, `FloatMap`, `{Int,Float,Str}{Stack,Queue,PQ}`, and `Json` (`json_parse`). All objects share the per-run 65,536-object arena. `Json` roots count once, child navigation (`at`, `get`) borrows without consuming arena slots, and `Json.keys()` allocates a `StrList`. Stacks and queues iterate in non-consuming pop order through `len`/`at`; priority queues use a stable min-heap with insertion-sequence ties (signed zero priorities tie).

## v3 byte codecs and map snapshots (181 signatures)
Adds 10 signatures on top of v2 (`lmcode-cpp` `38bbd918b7fde81db15d1d7427955cc5465d6f35`):
- `str_base64_encode(string) -> string` and `str_base64_decode(string) -> string`: canonical padded RFC 4648 section 4 Base64 over raw `VmString` bytes, rejecting whitespace, URL-safe symbols (`-`/`_`), unpadded or interior-padded input, and nonzero pad bits with `RUN_HOST` (`str_base64_decode: invalid encoding`).
- `str_hex_encode(string) -> string` and `str_hex_decode(string) -> string`: lowercase ASCII hex encoding and strict even-length mixed-case ASCII hex decoding (`0-9`, `a-f`, `A-F`), rejecting prefixes, whitespace, and odd length with `RUN_HOST` (`str_hex_decode: invalid encoding`).
- `StrMap.keys() -> StrList`, `StrMap.values() -> StrList`, `IntMap.keys() -> StrList`, `IntMap.values() -> IntList`, `FloatMap.keys() -> StrList`, and `FloatMap.values() -> FloatList`: each allocates one independent typed list in the shared 65,536-object arena in live insertion order, preserving raw bytes and float payload bits without aliasing map storage.

## Forward templates and invocation results (206 signatures, still v3)

The forward template function adds one signature to the original 181. Slice 2
ports the 24 invocation-result imports from C++ 3379c77, synchronized through
14868f84188abaf46d094e61abd291013d9bae1e. The schema format/version is unchanged.
Canonical generated declarations/docs are in std/ and docs/std.md. Hosts must
bind a ResultSink explicitly; no standard region reset clears it.
