# DSLB v6 rule entries

R49 appends the u16 entry table after body source lines. Pools/imports/context are shared; each entry owns locals, stack reservation, code, loops and line mappings. Parameter locals start at zero, are visible and match the canonical interface signature. Entry verification always runs its stack pass, including loop-free entries: HALT depth 0 for void and 1 otherwise. Body verification remains unchanged.

Program.Entry is immutable metadata; entries() is an immutable source-order list. call(index, context, registry, args) initializes fresh locals and delegates to the same Execution.execute path used by run(). No prior body run is required. run() executes the body only. Arity is RUN_HOST with R49's exact "expects N argument(s); found M" wording. Value kinds are host responsibility and returned values are untyped.

Core 70 and stdlib 267 vectors are pinned to C++ d608ba2. The unmodified mono 0e621804 generator's runtime-empty-lines-fallback truncates the v6 entry count, producing 69/70 with a correct loader. The canonical C++ corpus fixes just that fixture by preserving the entry tail. Independent regeneration with that one scratch-only generator correction matched all 70 canonical files; the mono checkout was not edited. No expected result or loader refusal was weakened.

EntryChecks first failed all 14 loader oracles against the pre-v6 runtime. It also covers immutable metadata, call before run, fresh parameters, untyped arguments/results, void, arity, index bounds, runtime location and instruction budget. A separate failing control caught lossy int64 JSON call arguments; only the new argument-reader path requests exact numbers, preserving existing schema/manifest parsing.
