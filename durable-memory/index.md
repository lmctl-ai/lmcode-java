# Durable memory

Technical documentation, architecture decisions, and operational guides for `lmcode-java`.

| File | Contents |
| --- | --- |
| [README.md](README.md) | Durable memory overview and conventions |
| [01-scope.md](01-scope.md) | SDK and versioning boundaries |
| [02-api.md](02-api.md) | Embedding API and byte semantics |
| [03-limits.md](03-limits.md) | Execution quotas and collection lifetimes |
| [04-build-test.md](04-build-test.md) | Pinned conformance vectors and GraalVM native builds |
| [05-ci-release.md](05-ci-release.md) | 5-platform matrix and release gate |
| [06-examples.md](06-examples.md) | Reproducible examples and demo gallery |
| [06-stdlib-catalog.md](06-stdlib-catalog.md) | Canonical standard catalog v1–v3 (181 signatures) |
| [07-open-items.md](07-open-items.md) | Remaining distribution and platform items |
| [autorun-v010.json](autorun-v010.json) | v0.1.0 fresh-directory autorun verification manifest |
| [release-v010.json](release-v010.json) | v0.1.0 release assets and platform checksums |
| [autorun-v011.json](autorun-v011.json) | v0.1.1 six fresh-cache native autorun checks |
| [release-v011.json](release-v011.json) | v0.1.1 release assets, catalog counts and platform checks |

## Adding an example or host function
1. Add an example under `examples/` with compiled `.dslb` bytecode.
2. Verify execution using the GraalVM native runner or `./gradlew test`.
3. Add tests verifying schema signature and output.

## Releases
Releases are cut via GitHub Actions when pushing a version tag `v*`, producing 5 native platform binaries with SHA256SUMS.
- [update-sample-suite.md](update-sample-suite.md): canonical sample-suite update and all-port verification rule.

- `catalog-catchup-v011.md` — current53b9bdf pins,169 vectors, managed byte/file contracts and v0.1.1 proof.
- [rule-methods-pin.md](rule-methods-pin.md) — compiler R47/R48 (schema `interfaces`, `@NAME`/`implements`): no runtime change, pin test and fixture regeneration.
- [08-lmscript-tests.md](08-lmscript-tests.md): lmscript test entry point, annotated interface example and native sample release convention.
