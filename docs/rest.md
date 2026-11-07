# REST server over stdio (`rest` std module)

Opt-in module of [spec/std-rest.md](https://github.com/lmctl-ai/lmcode/blob/main/spec/std-rest.md). There is **no network
listener**: the host process reads HTTP/1.1 requests from stdin and writes responses to stdout. `new Registry()` does not
register it (the default catalog stays at 206 signatures); a host opts in with `Rest.registerRest`.

```java
import io.lmctl.lmcode.*;
import io.lmctl.lmcode.rest.*;

Registry registry = new Registry();
RestController rest = Rest.registerRest(registry, new RestOptions()
    .input(System.in).output(System.out)            // the defaults
    .maxBodyBytes(1 << 20)                          // 413 above this Content-Length
    .onHandlerError((entry, message) -> System.err.println(entry + ": " + message)));
Program program = Program.load(bytecode, hostSchemaBytes, registry);   // host schema includes lmcode-rest.schema.json
rest.run(program, new Context());   // body: rest_route(...) then rest_serve(); then serves until EOF / rest_stop() / framing error
```

* `rest.dispatch(new RestHttp.Request(method, target, headers, body))` is the stream-independent core used by the unit tests.
* Keep stdout clean while serving: `print` and diagnostics belong on stderr.
* Handlers run one at a time, never nested in the body's VM run. No sockets, no threads, standard library only.

Try it (the shared conformance program):

```bash
mvn -q compile
printf 'GET /health HTTP/1.1\r\nHost: x\r\n\r\n' | java -cp target/classes io.lmctl.lmcode.rest.RestServe \
  conformance/rest-suite/app.dslb conformance/rest-suite/host.schema.json
```

`RestServe PROGRAM.dslb HOST.schema.json [--max-body-bytes N]` exits 0 after serving, 3 on error.

## Tests and pinning

* `mvn verify` runs `RestChecks` (26 named checks: router, head/body framing, registration, validation, exchange rules, and the
  shared suite through in-memory streams **and** a real child process over stdin/stdout) and verifies `rest-suite.lock`.
* `conformance/rest-suite` is copied from the spec repository and pinned by `rest-suite.lock` (portable digest: sha256 over
  `<sha256(file)>  <path>` rows sorted bytewise). `scripts/sync-rest.sh` re-copies it from `$LMCODE_SPEC_DIR` (default
  `../lmcode`) and rewrites the lock; `scripts/sync-rest.sh --verify` only checks it.
