# Standard REST Endpoints & Stateless CLI Interfaces (`lmcode_std_v3` 226 Signatures)

## Summary

`lmcode-java` implements the expanded `lmcode_std_v3` 226-signature standard catalog and `lmcode_std_rest_v1` controller parity with `lmcode-cpp`, `lmcode-rust`, `lmcode-go`, `lmcode-js`, and `lmcode-csharp`:

1. **Standard Catalog (`Stdlib.java`, `Rest.java`)**:
   - `Stdlib.register(registry)` installs all 226 canonical signatures from `std/lmcode-std.schema.json`, including `rest_config(string, string) -> void`, `rest_route(string, string, string) -> void`, `rest_serve() -> void`, `rest_stop() -> void`, `rest_cli(StrList) -> string`, and the 15 `RestExchange` methods.
   - `Rest.registerRest(registry, options)` uses compatibility registration (`beginCompatibilityRegistration()` / `endCompatibilityRegistration()`) to re-bind those 20 signatures to a dedicated `RestController` instance without adding non-standard host signatures or overriding conflicting opt-out host callbacks.
2. **Annotated Standard Interfaces (`RestController.java`, `RestRouter.java`)**:
   - Supports `RestGet`, `RestPost`, `RestPut`, `RestDelete`, `RestPatch`, `RestHead`, `RestOptions`, `RestHandler`, `CliCommand`, and `CliOption`.
   - Route templates accept both `{param}` / `{rest...}` and `:param` / `:rest...` placeholders.
   - `rest_config("prefix", "/api")` and `rest_config("max_body_bytes", "...")` configure auto-route prefixing and body limits before `rest_serve()`.
   - `rest_cli(argv)` dispatches stateless CLI invocations across `CliOption`, `CliCommand`, and REST routes using `Program.callIsolated()` so nested entry invocations inside an active run do not trip the top-level re-entrancy guard or clear active collection arenas.

## Verification

- `lmscript test.lms` (`mvn -q verify`): `PinVerifier` (226 canonical std signatures), `SelfTest`, `RestChecks` (28/28 REST checks including 39 in-memory + 39 child-process stdio conformance scenarios, annotated interfaces, `:param` routes, and `rest_cli`), 28 native invocation goldens, 29 metadata goldens, 9 return fixtures, 410 core vectors, 271 stdlib vectors, `RuleEntries`, and `Greeter`.
