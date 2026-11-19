package io.lmctl.lmcode.rest;

import io.lmctl.lmcode.*;
import io.lmctl.lmcode.rest.RestHttp.*;
import io.lmctl.lmcode.rest.RestRouter.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Named checks for the opt-in {@code rest} std module: unit tests plus replay of the shared stdio conformance suite. */
public final class RestChecks {
  private RestChecks() {}

  private static final Path SUITE = Path.of("conformance", "rest-suite");
  private static final String HANDLER = RestController.HANDLER_INTERFACE;
  private static int passed, failed;

  interface Body { void run() throws Exception; }

  static void test(String name, Body body) {
    try { body.run(); passed++; System.out.println("PASS " + name); }
    catch (Throwable e) { failed++; System.out.println("FAIL " + name); e.printStackTrace(System.out); }
  }

  static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
  static void equal(Object actual, Object expected, String what) {
    if (!Objects.equals(actual, expected)) throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
  }
  static void throwsHost(Runnable action, String messagePart, String what) {
    try { action.run(); } catch (VmException e) {
      check(e.code.equals("RUN_HOST"), what + ": code " + e.code);
      check(e.getMessage().contains(messagePart), what + ": message <" + e.getMessage() + "> lacks <" + messagePart + ">");
      return;
    }
    throw new AssertionError(what + ": expected RUN_HOST " + messagePart);
  }

  static byte[] bytes(String latin1) { return latin1.getBytes(StandardCharsets.ISO_8859_1); }
  static String latin1(byte[] b) { return new String(b, StandardCharsets.ISO_8859_1); }
  static byte[] get(String target) { return bytes("GET " + target + " HTTP/1.1\r\nHost: x\r\n\r\n"); }
  static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] p : parts) out.writeBytes(p);
    return out.toByteArray();
  }

  // ---- fixtures -------------------------------------------------------------------------------------------
  static Program.Entry entry(String annotation, String iface, String name) { return new Program.Entry(name, annotation, iface, List.of("RestExchange"), "void"); }
  static Program.Entry handler(String annotation) { return entry(annotation, HANDLER, annotation); }

  static RestController controllerWith(Program.Entry... entries) {
    RestController c = new RestController(new Registry(), new RestOptions());
    c.bindEntries(List.of(entries), (i, x) -> {});
    return c;
  }

  static Map<String, Object> scenarios;
  static long maxBody;

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> group(String name) { return (List<Map<String, Object>>) (List<?>) Json.array(scenarios.get(name)); }

  static byte[] requestBytes(Map<String, Object> s) {
    if (s.containsKey("raw")) return bytes((String) s.get("raw"));
    byte[] body;
    if (s.containsKey("body_repeat")) {
      Map<String, Object> rep = Json.object(s.get("body_repeat"));
      body = ((String) rep.get("text")).repeat(((Number) rep.get("count")).intValue()).getBytes(StandardCharsets.UTF_8);
    } else body = ((String) s.getOrDefault("body", "")).getBytes(StandardCharsets.UTF_8);
    StringBuilder head = new StringBuilder(s.get("method") + " " + s.get("target") + " HTTP/1.1\r\nHost: localhost\r\n");
    if (s.containsKey("headers")) Json.object(s.get("headers")).forEach((k, v) -> head.append(k).append(": ").append(v).append("\r\n"));
    if (body.length > 0 || List.of("POST", "PUT", "PATCH").contains(s.get("method"))) head.append("Content-Length: ").append(body.length).append("\r\n");
    head.append("\r\n");
    return concat(bytes(head.toString()), body);
  }

  record Reply(int status, Map<String, String> headers, byte[] body, long length) {}

  /** Parse every response in order; {@code methods.get(i)} is the request method of response i (HEAD responses carry no body bytes). */
  static List<Reply> parseResponses(byte[] data, List<String> methods) {
    List<Reply> out = new ArrayList<>();
    int pos = 0;
    String text = latin1(data);
    while (pos < data.length) {
      int end = text.indexOf("\r\n\r\n", pos);
      if (end < 0) throw new AssertionError("incomplete response head: " + text.substring(pos));
      String[] lines = text.substring(pos, end).split("\r\n", -1);
      check(lines[0].matches("HTTP/1\\.1 \\d{3} .*"), "status line " + lines[0]);
      int status = Integer.parseInt(lines[0].substring(9, 12));
      Map<String, String> headers = new HashMap<>();
      for (int i = 1; i < lines.length; i++) { int c = lines[i].indexOf(':'); headers.put(lines[i].substring(0, c).toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim()); }
      long length = Long.parseLong(headers.get("content-length"));
      boolean head = out.size() < methods.size() && methods.get(out.size()).equals("HEAD");
      int start = end + 4;
      int stop = head ? start : start + (int) length;
      out.add(new Reply(status, headers, Arrays.copyOfRange(data, start, stop), length));
      pos = stop;
    }
    return out;
  }

  /** Runs one fresh controller over {@code input}, returns everything it wrote. */
  interface Serve { byte[] serve(byte[] input) throws Exception; }

  static Registry suiteRegistry() { return new Registry(); }

  static byte[] serveInMemory(byte[] input, RestOptions extra) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Registry registry = suiteRegistry();
    RestOptions options = extra == null ? new RestOptions().onHandlerError((entry, message) -> { }) : extra;
    options.input(new ByteArrayInputStream(input)).output(out).maxBodyBytes(maxBody);
    RestController rest = Rest.registerRest(registry, options);
    rest.run(Program.load(Files.readAllBytes(SUITE.resolve("app.dslb")), Files.readAllBytes(SUITE.resolve("host.schema.json")), registry), new Context());
    return out.toByteArray();
  }

  static byte[] serveProcess(byte[] input) throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    String classes = Path.of(RestServe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    Process child = new ProcessBuilder(java, "-cp", classes, "io.lmctl.lmcode.rest.RestServe",
      SUITE.resolve("app.dslb").toString(), SUITE.resolve("host.schema.json").toString(), "--max-body-bytes", String.valueOf(maxBody))
      .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    Thread writer = new Thread(() -> {
      try (OutputStream stdin = child.getOutputStream()) { stdin.write(input); } catch (IOException ignored) { /* the server may stop reading early */ }
    });
    writer.start();
    byte[] out = child.getInputStream().readAllBytes();
    int code = child.waitFor();
    writer.join();
    equal(code, 0, "rest-serve exit code");
    return out;
  }

  static void expect(Reply r, Map<String, Object> expect, String label) {
    equal(r.status(), ((Number) expect.get("status")).intValue(), label + ": status");
    if (expect.containsKey("headers")) {
      Json.object(expect.get("headers")).forEach((name, want) -> {
        if (want == null) check(!r.headers().containsKey(name), label + ": header " + name + " must be absent");
        else equal(r.headers().get(name), want, label + ": header " + name);
      });
    }
    check(!r.headers().containsKey("connection"), label + ": no Connection header");
    if (expect.containsKey("body")) equal(new String(r.body(), StandardCharsets.UTF_8), expect.get("body"), label + ": body");
  }

  /** Replays the three groups; returns the number of checks (answered stream requests + fatal + silent). */
  static int replay(Serve serve) throws Exception {
    int count = 0;
    List<Map<String, Object>> answered = group("stream").stream().filter(s -> !Boolean.TRUE.equals(s.get("unanswered"))).toList();
    ByteArrayOutputStream all = new ByteArrayOutputStream();
    for (Map<String, Object> s : group("stream")) all.writeBytes(requestBytes(s));
    List<Reply> replies = parseResponses(serve.serve(all.toByteArray()), answered.stream().map(s -> (String) s.get("method")).toList());
    equal(replies.size(), answered.size(), "stream: exactly one response per answered request, none after rest_stop()");
    for (int i = 0; i < answered.size(); i++) {
      Map<String, Object> s = answered.get(i);
      expect(replies.get(i), Json.object(s.get("expect")), s.get("name") + " (" + s.get("method") + " " + s.get("target") + ")");
      long want = replies.get(i).length();
      if (!s.get("method").equals("HEAD")) equal((long) replies.get(i).body().length, want, s.get("name") + ": Content-Length equals body length");
      count++;
    }
    byte[] good = get("/health");
    for (Map<String, Object> s : group("fatal")) {
      List<Reply> r = parseResponses(serve.serve(concat(requestBytes(s), good)), List.of((String) s.get("method")));
      equal(r.size(), 1, s.get("name") + ": one error response, then the end");
      expect(r.get(0), Json.object(s.get("expect")), (String) s.get("name"));
      count++;
    }
    for (Map<String, Object> s : group("silent")) {
      equal(serve.serve(requestBytes(s)).length, 0, s.get("name") + ": no output");
      count++;
    }
    return count;
  }

  // ---- registration helpers ---------------------------------------------------------------------------------
  static Value call(Registry registry, String canonical, Value self, Value... args) {
    Registry.Binding b = registry.resolve(canonical);
    if (b == null) throw new AssertionError("not registered: " + canonical);
    return b.callback().call(new Context(), self, args);
  }
  static Value s(String text) { return Value.ofString(new VmString(bytes(text))); }

  public static void main(String[] args) throws Exception {
    scenarios = Json.object(Json.parse(Files.readAllBytes(SUITE.resolve("scenarios.json"))));
    maxBody = ((Number) Json.object(scenarios.get("options")).get("max_body_bytes")).longValue();
    runAll();
    System.out.println((failed == 0 ? "PASS " : "FAIL ") + passed + "/" + (passed + failed) + " rest checks");
    if (failed != 0) throw new AssertionError("rest checks failed: " + failed);
  }

  static void runAll() {
    // ---- unit: routes ----
    test("route templates: valid shapes parse into literal, param and rest segments", () -> {
      Route r = RestRouter.parseRoute("GET", "/users/{id}/files/{path...}");
      equal(r.segments.stream().map(RestRouter.Segment::kind).toList(), List.of(Kind.LITERAL, Kind.PARAM, Kind.LITERAL, Kind.REST), "kinds");
      equal(RestRouter.parseRoute("GET", "/").segments.size(), 1, "root");
      equal(RestRouter.parseRoute("GET", "/files/").segments.getLast().value(), "", "empty last segment");
    });
    test("route templates: invalid shapes are RUN_HOST errors with pinned text", () -> {
      String[][] bad = {
        {"get", "/x", "rest: invalid route get /x: method must be one of GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS"},
        {"GET", "x", "rest: invalid route GET x: path must start with '/'"},
        {"GET", "/a b", "rest: invalid route GET /a b: path must not contain whitespace, control characters, ? or #"},
        {"GET", "/a?b", "path must not contain whitespace, control characters, ? or #"},
        {"GET", "/a#b", "path must not contain whitespace"},
        {"GET", "/a//b", "rest: invalid route GET /a//b: empty segment ('//')"},
        {"GET", "/{1x}", "invalid parameter name '1x'"},
        {"GET", "/{}", "invalid parameter name ''"},
        {"GET", "/{a}/{a}", "duplicate parameter 'a'"},
        {"GET", "/{a...}/x", "'{a...}' must be the last segment"},
        {"GET", "/a{b}", "malformed placeholder in segment 'a{b}'"},
        {"GET", "/{b", "malformed placeholder in segment '{b'"}};
      for (String[] b : bad) throwsHost(() -> RestRouter.parseRoute(b[0], b[1]), b[2], b[0] + " " + b[1]);
      RestRouter.parseRoute("GET", "/cafÃ "); // UTF-8 bytes such as C3 A0 are ordinary
      RestRouter.parseRoute("GET", "/caf ");        // and so is a lone 0xA0
      throwsHost(() -> RestRouter.parseRoute("GET", "/a\u007fb"), "control characters", "DEL");
    });
    test("route table: duplicates are detected by shape, parameter names do not matter", () -> {
      RouteTable t = new RouteTable();
      t.add("GET", "/a/{x}", "one"); t.add("POST", "/a/{x}", "two");
      throwsHost(() -> t.add("GET", "/a/{y}", "three"), "rest: duplicate route GET /a/{y}", "dup");
      t.add("GET", "/a/b", "literal");
      t.add("GET", "/b/{x...}", "rest");
      throwsHost(() -> t.add("GET", "/b/{y...}", "rest2"), "duplicate route", "rest dup");
    });
    test("route table: specificity is literal > {param} > {rest...}, first difference wins", () -> {
      RouteTable t = new RouteTable();
      t.add("GET", "/x/{rest...}", "rest"); t.add("GET", "/x/{p}/z", "param-z"); t.add("GET", "/x/{p}/{q}", "param-param");
      t.add("GET", "/x/y/{q}", "literal-param"); t.add("GET", "/x/y/z", "literal-literal");
      equal(t.match("GET", "/x/y/z").route().annotation, "literal-literal", "y/z");
      equal(t.match("GET", "/x/y/w").route().annotation, "literal-param", "y/w");
      equal(t.match("GET", "/x/a/z").route().annotation, "param-z", "a/z");
      equal(t.match("GET", "/x/a/b").route().annotation, "param-param", "a/b");
      equal(t.match("GET", "/x/a/b/c").route().annotation, "rest", "a/b/c");
      equal(t.match("GET", "/x//a").params().get("rest"), "/a", "leading empty rest segment");
      equal(t.match("GET", "/x///a").params().get("rest"), "//a", "multiple leading empty rest segments");
      equal(t.match("GET", "/x/").route().annotation, "rest", "x/");
      equal(t.match("GET", "/x").status(), 404, "x");
    });
    test("route table: 404 vs 405, Allow header, HEAD fallback", () -> {
      RouteTable t = new RouteTable();
      t.add("GET", "/r", "g"); t.add("DELETE", "/r", "d"); t.add("PUT", "/only-put", "p");
      equal(t.match("POST", "/r").status(), 405, "405"); equal(t.match("POST", "/r").allow(), "DELETE, GET, HEAD", "allow");
      equal(t.match("GET", "/only-put").allow(), "PUT", "allow put");
      equal(t.match("GET", "/missing").status(), 404, "404");
      equal(t.match("HEAD", "/r").route().method, "GET", "head falls back to GET");
      equal(t.match("HEAD", "/only-put").status(), 405, "head without GET");
      equal(t.match("OPTIONS", "/r").status(), 405, "options is never implicit");
    });
    test("route table: encoded slashes stay inside one segment; parameters are decoded, invalid escapes never match", () -> {
      RouteTable t = new RouteTable();
      t.add("GET", "/f/{name}", "f");
      equal(t.match("GET", "/f/a%2Fb").params().get("name"), "a/b", "%2F");
      equal(t.match("GET", "/f/a%2Fb/c").status(), 404, "extra segment");
      equal(t.match("GET", "/f/%zz").status(), 404, "bad escape");
      equal(t.match("GET", "/f/").status(), 404, "empty segment is not a {param}");
      equal(RestRouter.percentDecode("%C3%B6", false), "Ã¶", "decoding yields bytes, not code points");
      equal(RestRouter.percentDecode("a+b", true), "a b", "plus in queries");
      equal(RestRouter.percentDecode("a+b", false), "a+b", "plus in paths");
      equal(RestRouter.percentDecode("%4", false), null, "short escape");
      equal(RestRouter.percentDecode("%", false), null, "lone percent");
      equal(RestRouter.percentDecode("%4G", false), null, "non-hex");
      equal(RestRouter.percentDecode("%aF", false), "¯", "mixed-case hex");
    });

    // ---- unit: HTTP subset ----
    test("http: request heads", () -> {
      Head h = RestHttp.parseHead("GET /a?b=1 HTTP/1.1\r\nHost: x\r\nX-A:  v \t ");
      equal(h.method(), "GET", "method"); equal(h.target(), "/a?b=1", "target");
      equal(h.headers(), List.of(new Header("Host", "x"), new Header("X-A", "v")), "headers trimmed of spaces and tabs");
      equal(RestHttp.parseHead("GET /a HTTP/1.0").error(), 0, "HTTP/1.0");
      for (String bad : new String[] {"garbage", "GET /a HTTP/2.0", "GET a HTTP/1.1", "GET /a#f HTTP/1.1", "get /a HTTP/1.1", "GET /a HTTP/1.1\r\nNoColon",
          "GET /a HTTP/1.1\r\n: v", "GET /a HTTP/1.1\r\nBad Name: v", "GET /a HTTP/1.1\r\nX: a\u0001b", "GET  /a HTTP/1.1", "GET /a b HTTP/1.1"})
        equal(RestHttp.parseHead(bad).error(), 400, bad);
      equal(RestHttp.parseHead("GET /cafÃ  HTTP/1.1").error(), 0, "0xA0 is an ordinary byte in a target");
      equal(RestHttp.parseHead("GET /a HTTP/1.1\r\nX:  v ").headers().get(0).value(), " v ", "values are trimmed of spaces/tabs only");
    });
    test("http: content-length rules", () -> {
      equal(RestHttp.bodyLength(List.of()), new BodyLength(0, 0), "none");
      equal(RestHttp.bodyLength(List.of(new Header("content-length", "12"))), new BodyLength(12, 0), "12");
      equal(RestHttp.bodyLength(List.of(new Header("Content-Length", "1"), new Header("content-length", "1"))), new BodyLength(1, 0), "equal duplicates");
      equal(RestHttp.bodyLength(List.of(new Header("Content-Length", "1"), new Header("content-length", "2"))).error(), 400, "conflicting duplicates");
      for (String v : new String[] {"-1", "1.5", "abc", "", "1".repeat(16), "+1", "1 2"})
        equal(RestHttp.bodyLength(List.of(new Header("Content-Length", v))).error(), 400, "length " + v);
      equal(RestHttp.bodyLength(List.of(new Header("Transfer-Encoding", "chunked"))).error(), 501, "chunked");
    });
    test("http: response encoding has no Connection header and the GET length for HEAD", () -> {
      equal(latin1(RestHttp.encodeResponse(new Response(200, List.of(new Header("A", "b")), bytes("hi")), false)), "HTTP/1.1 200 OK\r\nA: b\r\nContent-Length: 2\r\n\r\nhi", "full");
      equal(latin1(RestHttp.encodeResponse(new Response(299, List.of(), bytes("hi")), true)), "HTTP/1.1 299 OK\r\nContent-Length: 2\r\n\r\n", "head");
      equal(latin1(RestHttp.encodeResponse(new Response(418, List.of(), new byte[0]), false)), "HTTP/1.1 418 Client Error\r\nContent-Length: 0\r\n\r\n", "unknown reason");
    });
    test("takeRequest: incremental framing, fatal errors and pipelined remainders", () -> {
      byte[] full = bytes("POST /a HTTP/1.1\r\nContent-Length: 3\r\n\r\nabcGET /b HTTP/1.1\r\n\r\n");
      Taken one = RestHttp.takeRequest(full, full.length, 1024);
      equal(latin1(one.request().body()), "abc", "body");
      byte[] rest = Arrays.copyOfRange(full, one.consumed(), full.length);
      equal(RestHttp.takeRequest(rest, rest.length, 1024).request().target(), "/b", "second request");
      check(RestHttp.takeRequest(bytes("GET /a HTT"), 10, 1024).need(), "partial head");
      byte[] partial = bytes("POST /a HTTP/1.1\r\nContent-Length: 5\r\n\r\nab");
      check(RestHttp.takeRequest(partial, partial.length, 1024).need(), "partial body");
      byte[] big = bytes("POST /a HTTP/1.1\r\nContent-Length: 2000\r\n\r\n");
      equal(RestHttp.takeRequest(big, big.length, 1024).fatal(), 413, "413 from the header alone");
      byte[] flood = new byte[20000]; Arrays.fill(flood, (byte) 'a');
      equal(RestHttp.takeRequest(flood, flood.length, 1024).fatal(), 431, "431 before the head ends");
      byte[] bad = bytes("nonsense\r\n\r\n");
      equal(RestHttp.takeRequest(bad, bad.length, 1024).fatal(), 400, "400");
      byte[] te = bytes("POST /a HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n");
      equal(RestHttp.takeRequest(te, te.length, 1024).fatal(), 501, "501");
    });

    // ---- unit: registration and exchange rules ----
    test("registration: all 20 declared signatures are in the default registry and bare registerRest", () -> {
      Registry plain = new Registry();
      int before = plain.signatures().size();
      equal(before, 226, "default catalog has 226 signatures");
      List<String> declared = SchemaSignatures.from(Files.readAllBytes(SUITE.resolve("host.schema.json")));
      equal(declared.size(), 20, "declared signatures");
      equal(plain.signatures().stream().filter(x -> x.contains("rest_") || x.contains("RestExchange")).toList(), declared, "registered = declared");
      Rest.registerRest(plain);
      equal(plain.signatures().stream().filter(x -> x.contains("rest_") || x.contains("RestExchange")).toList(), declared, "registered = declared");
      equal(plain.signatures().size(), 226, "226");
      equal(plain.stdSignatures().size(), 226, "std signatures");
      plain.checkSchemaSignatures(Files.readAllBytes(SUITE.resolve("host.schema.json")));
      Registry bare = new Registry(false);
      Rest.registerRest(bare);
      equal(bare.signatures().size(), 20, "bare registerRest");
    });
    test("annotated std interfaces and colon params auto-register routes and support stateless CLI", () -> {
      Route colon = RestRouter.parseRoute("GET", "/users/:id/files/:path...");
      equal(colon.segments.size(), 4, "segments");
      equal(colon.segments.get(1).kind(), Kind.PARAM, "param kind");
      equal(colon.segments.get(1).value(), "id", "param name");
      equal(colon.segments.get(3).kind(), Kind.REST, "rest kind");
      equal(colon.segments.get(3).value(), "path", "rest name");

      RestController c = controllerWith(
        entry("/items/:id", RestController.REST_GET_INTERFACE, "get_item"),
        entry("/items", RestController.REST_POST_INTERFACE, "post_item"),
        entry("DELETE /items/:id", RestController.HANDLER_INTERFACE, "del_item"),
        entry("status", RestController.CLI_COMMAND_INTERFACE, "cmd_status"),
        entry("--verbose", RestController.CLI_OPTION_INTERFACE, "opt_verbose")
      );
      c.config("prefix", "/api");
      c.serve();
      check(c.serving(), "serving");
      equal(c.table.match("GET", "/api/items/42").status(), 0, "GET /api/items/42");
      equal(c.table.match("GET", "/api/items/42").params().get("id"), "42", "id=42");
      equal(c.table.match("POST", "/api/items").status(), 0, "POST /api/items");
      equal(c.table.match("DELETE", "/api/items/42").status(), 0, "DELETE /api/items/42");
      equal(c.table.match("GET", "/api/status").status(), 0, "GET /api/status");
    });
    test("serve validation happens before any byte is read", () -> {
      RestController none = controllerWith();
      throwsHost(none::serve, "rest: no routes registered", "none");
      RestController missing = controllerWith();
      missing.route("GET", "/a", "nope");
      throwsHost(missing::serve, "rest: route GET /a references missing annotation @nope", "missing");
      RestController wrong = controllerWith(entry("a", "interface Rule(Node) -> void", "check"));
      wrong.route("GET", "/a", "a");
      throwsHost(wrong::serve, "rest: entry check must implement RestHandler(RestExchange) -> void", "wrong");
      RestController dup = controllerWith(handler("a"), handler("a"));
      dup.route("GET", "/a", "a");
      throwsHost(dup::serve, "rest: duplicate annotation @a", "dup");
      RestController unbound = new RestController(new Registry(), new RestOptions());
      unbound.route("GET", "/a", "a");
      throwsHost(unbound::serve, "rest: no program is bound (run the program through the controller)", "unbound");
      for (RestController c : List.of(none, missing, wrong, dup, unbound)) check(!c.serving(), "not serving after a failed rest_serve");
    });
    test("serve validation ignores entries that implement other interfaces", () -> {
      RestController c = controllerWith(handler("a"), entry("other", "interface Rule(Node) -> void", "other"));
      c.route("GET", "/a", "a");
      c.serve();
      check(c.serving(), "serving");
    });
    test("lifecycle: routes freeze after rest_serve, a second rest_serve fails", () -> {
      RestController c = controllerWith(handler("a"));
      c.route("GET", "/a", "a");
      c.serve();
      throwsHost(() -> c.route("GET", "/b", "a"), "rest: routes are frozen once rest_serve has been called", "frozen");
      throwsHost(c::serve, "rest: rest_serve already called", "twice");
    });
    test("exchange rules: status range, header validation, reserved headers, counters", () -> {
      Registry registry = new Registry();
      RestController c = Rest.registerRest(registry);
      RestController.Exchange x = new RestController.Exchange(c);
      Value self = Value.object("RestExchange", x);
      RestController.Exchange emptyHeader = new RestController.Exchange(c,
          new Request("GET", "/", List.of(new Header("X-Empty", "")), new byte[0]), Map.of(), Map.of(), "/");
      Value emptySelf = Value.object("RestExchange", emptyHeader);
      check(call(registry, "method RestExchange.has_header(string) -> bool", emptySelf, s("X-Empty")).asBool(), "present empty request header");
      equal(call(registry, "method RestExchange.header(string) -> string", emptySelf, s("X-Empty")).asString().toString(), "", "empty request header value");
      throwsHost(() -> call(registry, "method RestExchange.status(int) -> void", self, Value.ofInt(199)), "rest: status 199 out of range 200..599", "199");
      throwsHost(() -> call(registry, "method RestExchange.status(int) -> void", self, Value.ofInt(600)), "out of range", "600");
      call(registry, "method RestExchange.status(int) -> void", self, Value.ofInt(599));
      equal(x.status, 599, "status");
      String set = "method RestExchange.set_header(string, string) -> void";
      for (String name : new String[] {"", "a b", "a:b", "a\tb", "a\u007fb", "a\u0001b"})
        throwsHost(() -> call(registry, set, self, s(name), s("v")), "invalid response header", "name " + name.length());
      throwsHost(() -> call(registry, set, self, s("X"), s("a\r\nInjected: 1")), "invalid response header", "value CRLF");
      for (String name : new String[] {"Content-Length", "connection", "TRANSFER-ENCODING"})
        throwsHost(() -> call(registry, set, self, s(name), s("1")), "managed by the server", name);
      call(registry, set, self, s("X-A"), s("1"));
      call(registry, set, self, s("x-a"), s("2"));
      equal(new ArrayList<>(x.headers.values()), List.of(new Header("x-a", "2")), "same name replaces, case-insensitively");
      String inc = "method RestExchange.increment(string) -> int";
      equal(call(registry, inc, self, s("k")).asInt(), 1L, "first increment");
      equal(call(registry, inc, self, s("k")).asInt(), 2L, "second increment");
      equal(call(registry, "method RestExchange.get(string) -> string", self, s("k")).asString().toString(), "", "counters and strings are separate maps");
      c.counts.put("wrap", Long.MAX_VALUE);
      equal(call(registry, inc, self, s("wrap")).asInt(), Long.MIN_VALUE, "increment wraps as int64");
      throwsHost(() -> call(registry, "method RestExchange.body() -> string", Value.none()), "invalid exchange", "bad receiver");
    });

    // ---- integration: stdio ----
    test("conformance suite through in-memory streams (stream, fatal, silent groups)", () ->
      equal(replay(input -> serveInMemory(input, null)), 32 + 4 + 3, "checks"));
    test("conformance suite through a real child process: stdin to stdout, exit code 0", () ->
      equal(replay(RestChecks::serveProcess), 32 + 4 + 3, "checks"));
    test("stdio: harness composes valid HTTP for every stream scenario", () -> {
      for (Map<String, Object> s : group("stream")) {
        byte[] r = requestBytes(s);
        check(!RestHttp.takeRequest(r, r.length, 1 << 20).need() && RestHttp.takeRequest(r, r.length, 1 << 20).request() != null, "" + s.get("name"));
      }
    });
    test("stdio: a request fed one byte at a time (mid-header and mid-body) is reassembled", () -> {
      byte[] req = bytes("POST /sum HTTP/1.1\r\nHost: x\r\nContent-Length: 21\r\n\r\n{\"values\":[10,20,12]}");
      InputStream trickle = new InputStream() {
        int i = 0;
        @Override public int read() { return i < req.length ? req[i++] & 255 : -1; }
        @Override public int read(byte[] b, int off, int len) { if (i >= req.length) return -1; b[off] = req[i++]; return 1; }
      };
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Registry registry = new Registry();
      RestController rest = Rest.registerRest(registry, new RestOptions().input(trickle).output(out).maxBodyBytes(maxBody));
      rest.run(Program.load(Files.readAllBytes(SUITE.resolve("app.dslb")), Files.readAllBytes(SUITE.resolve("host.schema.json")), registry), new Context());
      Reply r = parseResponses(out.toByteArray(), List.of("POST")).get(0);
      equal(r.status(), 201, "status"); equal(latin1(r.body()), "{\"sum\":42}", "body");
    });
    test("stdio: each response is written before the next request is read", () -> {
      List<String> events = new ArrayList<>();
      byte[][] chunks = {get("/health"), get("/count")};
      int[] next = {0};
      InputStream in = new InputStream() {
        @Override public int read() { throw new UnsupportedOperationException(); }
        @Override public int read(byte[] b, int off, int len) {
          if (next[0] >= chunks.length) return -1;
          events.add("read" + next[0]);
          byte[] c = chunks[next[0]++]; System.arraycopy(c, 0, b, off, c.length); return c.length;
        }
      };
      OutputStream out = new OutputStream() {
        @Override public void write(int b) { }
        @Override public void write(byte[] b, int off, int len) { events.add("write"); }
      };
      Registry registry = new Registry();
      RestController rest = Rest.registerRest(registry, new RestOptions().input(in).output(out));
      rest.run(Program.load(Files.readAllBytes(SUITE.resolve("app.dslb")), Files.readAllBytes(SUITE.resolve("host.schema.json")), registry), new Context());
      equal(events, List.of("read0", "write", "read1", "write"), "interleaving");
    });
    test("stdio: pipelined requests in one chunk are answered in order", () -> {
      byte[] out = serveInMemory(concat(get("/hello/a"), get("/hello/b"), get("/nope"), get("/hello/c")), null);
      equal(parseResponses(out, List.of()).stream().map(Reply::status).toList(), List.of(200, 200, 404, 200), "statuses");
    });
    test("stdio: handler errors reach onHandlerError with the entry name and never the client; serving continues", () -> {
      List<String[]> seen = new ArrayList<>();
      byte[] out = serveInMemory(concat(get("/boom"), get("/health")), new RestOptions().onHandlerError((e, m) -> seen.add(new String[] {e, m})));
      List<Reply> r = parseResponses(out, List.of());
      equal(r.get(0).status(), 500, "500"); equal(latin1(r.get(0).body()), "{\"error\":\"internal error\"}", "fixed body");
      equal(r.get(1).status(), 200, "keeps serving");
      equal(seen.size(), 1, "one callback"); equal(seen.get(0)[0], "boom", "entry name");
      check(seen.get(0)[1].toLowerCase(Locale.ROOT).contains("zero"), "VM message " + seen.get(0)[1]);
    });
    test("stdio: invalid percent escapes in the path or query are 400 before routing and do not end serving", () -> {
      byte[] out = serveInMemory(concat(get("/nope?x=%zz"), get("/hello/%zz"), get("/%zz/unrouted"), get("/health")), null);
      equal(parseResponses(out, List.of()).stream().map(Reply::status).toList(), List.of(400, 400, 400, 200), "statuses");
    });
    test("stdio: rest_stop() before the body ends means nothing is read", () -> {
      InputStream never = new InputStream() { @Override public int read() { throw new AssertionError("input must not be read"); } };
      RestController c = new RestController(new Registry(), new RestOptions().input(never).output(new ByteArrayOutputStream()));
      c.bindEntries(List.of(handler("a")), (i, x) -> {});
      c.serving = true; c.stopped = true;
      c.finish();
    });
    test("stdio: a closed output ends serving quietly", () -> {
      OutputStream broken = new OutputStream() { @Override public void write(int b) throws IOException { throw new IOException("Broken pipe"); } };
      Registry registry = new Registry();
      RestController rest = Rest.registerRest(registry, new RestOptions().input(new ByteArrayInputStream(get("/health"))).output(broken).maxBodyBytes(maxBody));
      rest.run(Program.load(Files.readAllBytes(SUITE.resolve("app.dslb")), Files.readAllBytes(SUITE.resolve("host.schema.json")), registry), new Context());
    });
    test("stdio: the suite lock matches the copied fixtures", () -> {
      Properties lock = new Properties();
      try (var in = Files.newInputStream(Path.of("rest-suite.lock"))) { lock.load(in); }
      equal(RestSuitePin.digest(SUITE), lock.getProperty("sha256"), "digest");
    });
    test("stdio: annotated REST interfaces, colon params, and rest_cli dispatch against app.dslb", () -> {
      Registry registry = suiteRegistry();
      RestController rest = Rest.registerRest(registry, new RestOptions().input(new ByteArrayInputStream(new byte[0])).output(new ByteArrayOutputStream()));
      rest.run(Program.load(Files.readAllBytes(SUITE.resolve("app.dslb")), Files.readAllBytes(SUITE.resolve("host.schema.json")), registry), new Context());
      Response rItem = rest.dispatch(new Request("GET", "/items/42/detail", List.of(), new byte[0]));
      equal(rItem.status(), 200, "item status"); equal(latin1(rItem.body()), "item:42", "item body");
      Response rAget = rest.dispatch(new Request("GET", "/annotated/get/world", List.of(), new byte[0]));
      equal(rAget.status(), 200, "aget status"); equal(latin1(rAget.body()), "annotated-get:world", "aget body");
      Response rApost = rest.dispatch(new Request("POST", "/annotated/post", List.of(), bytes("hello")));
      equal(rApost.status(), 201, "apost status"); equal(latin1(rApost.body()), "annotated-post:hello", "apost body");
      Response rAhand = rest.dispatch(new Request("GET", "/annotated/handler/ping", List.of(), new byte[0]));
      equal(rAhand.status(), 200, "ahand status"); equal(latin1(rAhand.body()), "annotated-handler:GET:ping", "ahand body");
      Response rCli = rest.dispatch(new Request("POST", "/cli-run", List.of(), bytes("--format=json scan -- item1")));
      equal(rCli.status(), 200, "cli status"); equal(latin1(rCli.body()), "cli:scan:json:item1", "cli body");
    });
  }
}
