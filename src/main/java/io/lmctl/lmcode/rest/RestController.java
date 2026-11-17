package io.lmctl.lmcode.rest;

import io.lmctl.lmcode.Context;
import io.lmctl.lmcode.Program;
import io.lmctl.lmcode.Registry;
import io.lmctl.lmcode.Value;
import io.lmctl.lmcode.VmException;
import io.lmctl.lmcode.rest.RestHttp.Header;
import io.lmctl.lmcode.rest.RestHttp.Request;
import io.lmctl.lmcode.rest.RestHttp.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * The {@code rest} std module: route table, transport-independent {@link #dispatch}, and the stdio pump that serves
 * HTTP/1.1 messages from an input stream to an output stream (spec/std-rest.md). No sockets, no threads.
 */
public final class RestController {
  public static final String HANDLER_INTERFACE = "interface RestHandler(RestExchange) -> void";
  public static final String REST_GET_INTERFACE = "interface RestGet(RestExchange) -> void";
  public static final String REST_POST_INTERFACE = "interface RestPost(RestExchange) -> void";
  public static final String REST_PUT_INTERFACE = "interface RestPut(RestExchange) -> void";
  public static final String REST_DELETE_INTERFACE = "interface RestDelete(RestExchange) -> void";
  public static final String REST_PATCH_INTERFACE = "interface RestPatch(RestExchange) -> void";
  public static final String REST_HEAD_INTERFACE = "interface RestHead(RestExchange) -> void";
  public static final String REST_OPTIONS_INTERFACE = "interface RestOptions(RestExchange) -> void";
  public static final String CLI_COMMAND_INTERFACE = "interface CliCommand(RestExchange) -> void";
  public static final String CLI_OPTION_INTERFACE = "interface CliOption(RestExchange) -> void";

  public static String methodForRestInterface(String iface) {
    return switch (iface) {
      case REST_GET_INTERFACE -> "GET";
      case REST_POST_INTERFACE -> "POST";
      case REST_PUT_INTERFACE -> "PUT";
      case REST_DELETE_INTERFACE -> "DELETE";
      case REST_PATCH_INTERFACE -> "PATCH";
      case REST_HEAD_INTERFACE -> "HEAD";
      case REST_OPTIONS_INTERFACE -> "OPTIONS";
      default -> null;
    };
  }

  public static boolean isRestOrCliInterface(String iface) {
    return iface.equals(HANDLER_INTERFACE) || methodForRestInterface(iface) != null ||
      iface.equals(CLI_COMMAND_INTERFACE) || iface.equals(CLI_OPTION_INTERFACE);
  }

  /** Calls entry {@code index} of the bound program (a seam so unit tests need no bytecode). */
  @FunctionalInterface interface EntryCaller { void call(int index, Value exchange); }

  /** Mutable per-request state behind a lmcode {@code RestExchange} value. */
  static final class Exchange {
    final RestController controller; Request request; Map<String, String> params; final Map<String, String> query; final String path;
    int status = 200; final Map<String, Header> headers = new LinkedHashMap<>(); String body = "";
    Exchange(RestController controller, Request request, Map<String, String> params, Map<String, String> query, String path) {
      this.controller = controller; this.request = request; this.params = params; this.query = query; this.path = path;
    }
    Exchange(RestController controller) { this(controller, new Request("GET", "/", List.of(), new byte[0]), Map.of(), Map.of(), "/"); }
    String requestHeaders(String name) {
      String wanted = RestHttp.lower(name);
      StringJoiner out = new StringJoiner(", ");
      boolean any = false;
      for (Header h : request.headers()) if (RestHttp.lower(h.name()).equals(wanted)) { out.add(h.value()); any = true; }
      return any ? out.toString() : null;
    }
  }

  final Registry registry;
  private final InputStream input;
  private final OutputStream output;
  private long maxBodyBytes;
  private final BiConsumer<String, String> onHandlerError;
  final Map<String, String> values = new HashMap<>();
  final Map<String, Long> counts = new HashMap<>();
  RestRouter.RouteTable table = new RestRouter.RouteTable();
  private List<Program.Entry> entries; private EntryCaller caller;
  private final Map<String, Integer> entryIndex = new HashMap<>();
  private final Map<String, String> entryName = new HashMap<>();
  private final Map<String, Integer> autoEntryIndex = new HashMap<>();
  private final Map<String, String> autoEntryName = new HashMap<>();
  private final Set<Integer> autoEntryIndices = new HashSet<>();
  private final Map<String, Integer> cliOptions = new HashMap<>();
  private String configPrefix = "";
  private boolean autoRoutesMaterialized;
  private boolean inControllerRun;
  private Program program; private Context context = new Context();
  boolean serving, stopped;

  public RestController(Registry registry, RestOptions options) {
    this.registry = registry;
    this.input = options.input; this.output = options.output;
    this.maxBodyBytes = options.maxBodyBytes; this.onHandlerError = options.onHandlerError;
  }

  /** True once the program body has called {@code rest_serve}. */
  public boolean serving() { return serving; }
  /** True once {@code rest_stop} (or {@link #stop}) has been called during the current run. */
  public boolean stopped() { return stopped; }

  boolean beforeBodyRun(Program program, Context context) {
    if (inControllerRun) return false;
    this.program = program;
    this.context = context;
    this.table = new RestRouter.RouteTable();
    this.serving = false;
    this.stopped = false;
    this.configPrefix = "";
    bindEntries(program.entries(), (index, exchange) -> program.callIsolated(index, context, registry, List.of(exchange)));
    return true;
  }

  /**
   * Binds {@code program}, runs its body, then (if the body called rest_serve and not rest_stop) serves the input
   * stream until EOF, rest_stop() or a framing error.
   */
  public void run(Program program, Context context) {
    inControllerRun = true;
    try {
      this.program = program; this.context = context; this.stopped = false;
      bindEntries(program.entries(), (index, exchange) -> program.callIsolated(index, context, registry, List.of(exchange)));
      program.run(context, registry);
      finish();
    } finally {
      inControllerRun = false;
    }
  }

  /** After the body: serve the streams if requested and not already stopped. */
  void finish() { if (serving && !stopped) pump(); }

  /** Test seam: bind entries and a caller without a {@link Program}. */
  void bindEntries(List<Program.Entry> entries, EntryCaller caller) {
    this.entries = entries;
    this.caller = caller;
    this.autoRoutesMaterialized = false;
    this.autoEntryIndex.clear();
    this.autoEntryName.clear();
    this.autoEntryIndices.clear();
    this.entryIndex.clear();
    this.entryName.clear();
    this.cliOptions.clear();
  }

  // ---- lmcode-facing operations -----------------------------------------------------------------------------
  public void config(String key, String value) {
    if (serving) throw VmException.host("rest: routes are frozen once rest_serve has been called");
    values.put(key, value);
    if (key.equals("prefix") || key.equals("base_path")) {
      configPrefix = value;
    } else if (key.equals("max_body_bytes") && !value.isEmpty() && value.chars().allMatch(c -> c >= '0' && c <= '9')) {
      try {
        long n = Long.parseLong(value);
        if (n > 0) maxBodyBytes = n;
      } catch (NumberFormatException ignored) {}
    }
  }

  public void route(String method, String path, String annotation) {
    if (serving) throw VmException.host("rest: routes are frozen once rest_serve has been called");
    table.add(method, path, annotation);
  }

  public void serve() {
    if (serving) throw VmException.host("rest: rest_serve already called");
    if (entries == null) throw VmException.host("rest: no program is bound (run the program through the controller)");
    materializeAutoRoutes();
    validate();
    serving = true;
  }

  /** Ends serving once the current response is written. */
  public void stop() { stopped = true; }

  private String normalizedPrefix() {
    if (configPrefix == null || configPrefix.isEmpty()) return "";
    String p = configPrefix.startsWith("/") ? configPrefix : "/" + configPrefix;
    while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
    return p.equals("/") ? "" : p;
  }

  private void materializeAutoRoutes() {
    if (autoRoutesMaterialized || entries == null) return;
    autoRoutesMaterialized = true;
    String prefix = normalizedPrefix();
    for (int i = 0; i < entries.size(); i++) {
      Program.Entry e = entries.get(i);
      if (e.iface().equals(CLI_OPTION_INTERFACE)) {
        cliOptions.put(e.annotation(), i);
        String stripped = e.annotation().replaceFirst("^-+", "");
        if (!stripped.isEmpty()) cliOptions.put(stripped, i);
        autoEntryIndices.add(i);
        continue;
      }
      String method, path;
      String m = methodForRestInterface(e.iface());
      if (m != null) {
        int sp = e.annotation().indexOf(' ');
        if (sp >= 0) { method = e.annotation().substring(0, sp); path = e.annotation().substring(sp + 1); }
        else if (e.annotation().startsWith("/")) { method = m; path = e.annotation(); }
        else continue;
      } else if (e.iface().equals(HANDLER_INTERFACE)) {
        int sp = e.annotation().indexOf(' ');
        if (sp >= 0) { method = e.annotation().substring(0, sp); path = e.annotation().substring(sp + 1); }
        else if (e.annotation().startsWith("/")) { method = "GET"; path = e.annotation(); }
        else continue;
      } else if (e.iface().equals(CLI_COMMAND_INTERFACE)) {
        int sp = e.annotation().indexOf(' ');
        if (sp >= 0) { method = e.annotation().substring(0, sp); path = e.annotation().substring(sp + 1); }
        else { method = "GET"; path = e.annotation(); }
        if (path.isEmpty() || !path.startsWith("/")) path = "/" + path;
      } else continue;

      if (!prefix.isEmpty() && !path.startsWith(prefix)) path = path.equals("/") ? prefix : prefix + path;
      String tag = "__auto_entry_" + i;
      table.add(method, path, tag);
      autoEntryIndex.put(tag, i);
      autoEntryName.put(tag, e.name());
      autoEntryIndices.add(i);
    }
  }

  private void ensureDispatchReady() {
    if (entries == null) return;
    materializeAutoRoutes();
    if (entryIndex.isEmpty() && !autoEntryIndex.isEmpty()) {
      entryIndex.putAll(autoEntryIndex);
      entryName.putAll(autoEntryName);
      for (int i = 0; i < entries.size(); i++) {
        Program.Entry e = entries.get(i);
        if (!isRestOrCliInterface(e.iface()) || e.iface().equals(CLI_OPTION_INTERFACE)) continue;
        if (autoEntryIndices.contains(i)) continue;
        entryIndex.putIfAbsent(e.annotation(), i);
        entryName.putIfAbsent(e.annotation(), e.name());
      }
    }
  }

  private void validate() {
    if (table.size() == 0) throw VmException.host("rest: no routes registered");
    Map<String, Integer> idx = new HashMap<>(autoEntryIndex);
    Map<String, String> names = new HashMap<>(autoEntryName);
    for (int i = 0; i < entries.size(); i++) {
      Program.Entry e = entries.get(i);
      if (!isRestOrCliInterface(e.iface()) || e.iface().equals(CLI_OPTION_INTERFACE)) continue;
      if (autoEntryIndices.contains(i)) continue;
      if (idx.containsKey(e.annotation())) throw VmException.host("rest: duplicate annotation @" + e.annotation());
      idx.put(e.annotation(), i); names.put(e.annotation(), e.name());
    }
    for (RestRouter.Route route : table.routes()) {
      if (idx.containsKey(route.annotation)) continue;
      for (Program.Entry e : entries)
        if (e.annotation().equals(route.annotation)) throw VmException.host("rest: entry " + e.name() + " must implement RestHandler(RestExchange) -> void");
      throw VmException.host("rest: route " + route.method + " " + route.path + " references missing annotation @" + route.annotation);
    }
    entryIndex.clear(); entryIndex.putAll(idx);
    entryName.clear(); entryName.putAll(names);
  }

  public String cli(List<String> argv) {
    if (entries == null || caller == null) throw VmException.host("rest: no program is bound (run the program through the controller)");
    ensureDispatchReady();

    String method = "GET";
    String body = "";
    boolean methodExplicit = false;
    List<String> positionals = new ArrayList<>();
    record OptVal(String flag, String key, String val) {}
    List<OptVal> parsedOpts = new ArrayList<>();

    for (int i = 0; i < argv.size(); i++) {
      String arg = argv.get(i);
      if (arg.equals("--")) {
        String restBody = String.join(" ", argv.subList(i + 1, argv.size()));
        if (body.isEmpty()) body = restBody;
        break;
      }
      if (arg.length() > 1 && arg.startsWith("-")) {
        String flag = arg, key, val = "true";
        int eq = arg.indexOf('=');
        if (eq >= 0) {
          flag = arg.substring(0, eq);
          val = arg.substring(eq + 1);
          key = flag.startsWith("--") ? flag.substring(2) : flag.substring(1);
        } else {
          key = flag.startsWith("--") ? flag.substring(2) : flag.substring(1);
          if (i + 1 < argv.size() && (argv.get(i + 1).isEmpty() || !argv.get(i + 1).startsWith("-"))) {
            String next = argv.get(i + 1);
            boolean isSpecial = key.equals("method") || key.equals("X") || key.equals("body") || key.equals("d");
            String probePath = next.startsWith("/") ? next : "/" + next;
            boolean nextIsRootCmd = positionals.isEmpty() && !isSpecial && table.match("GET", probePath).status() != 404;
            if (!nextIsRootCmd) val = argv.get(++i);
          }
        }
        if (key.equals("method") || key.equals("X")) { method = val.toUpperCase(Locale.ROOT); methodExplicit = true; }
        else if (key.equals("body") || key.equals("d")) body = val;
        parsedOpts.add(new OptVal(flag, key, val));
      } else positionals.add(arg);
    }

    String path = "/";
    if (!positionals.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < positionals.size(); i++) {
        String seg = positionals.get(i);
        if (i == 0 && seg.startsWith("/")) sb.append(seg);
        else sb.append('/').append(seg);
      }
      path = sb.isEmpty() ? "/" : sb.toString();
    }
    String prefix = normalizedPrefix();
    if (!prefix.isEmpty() && !path.startsWith(prefix)) path = path.equals("/") ? prefix : prefix + path;

    Map<String, String> query = new HashMap<>();
    for (OptVal opt : parsedOpts) {
      query.put(opt.key(), opt.val());
      query.put(opt.flag(), opt.val());
      values.put(opt.key(), opt.val());
    }

    Exchange x = new Exchange(this, new Request(method, path, List.of(), body.getBytes(StandardCharsets.ISO_8859_1)), Map.of(), query, path);
    for (OptVal opt : parsedOpts) {
      Integer target = cliOptions.getOrDefault(opt.flag(), cliOptions.get(opt.key()));
      if (target != null) caller.call(target, Value.object("RestExchange", x));
    }
    if (stopped) return new String(response(x).body(), StandardCharsets.ISO_8859_1);

    RestRouter.Match match = table.match(method, path);
    if (match.status() == 405 && !methodExplicit) {
      if (!body.isEmpty() && table.match("POST", path).status() == 0) {
        x.request = new Request("POST", path, x.request.headers(), x.request.body());
        match = table.match("POST", path);
      } else {
        for (String cand : List.of("POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")) {
          RestRouter.Match probe = table.match(cand, path);
          if (probe.status() == 0) {
            x.request = new Request(cand, path, x.request.headers(), x.request.body());
            match = probe;
            break;
          }
        }
      }
    }

    if (match.status() == 404) {
      if (positionals.isEmpty() && !x.body.isEmpty()) return new String(response(x).body(), StandardCharsets.ISO_8859_1);
      return new String(RestHttp.errorResponse(404).body(), StandardCharsets.ISO_8859_1);
    }
    if (match.status() == 405) return new String(RestHttp.errorResponse(405, new Header("Allow", match.allow())).body(), StandardCharsets.ISO_8859_1);
    Integer idx = entryIndex.get(match.route().annotation);
    if (idx == null) return new String(RestHttp.errorResponse(500).body(), StandardCharsets.ISO_8859_1);
    x.params = match.params();
    try {
      caller.call(idx, Value.object("RestExchange", x));
    } catch (RuntimeException | StackOverflowError error) {
      onHandlerError.accept(entryName.get(match.route().annotation), String.valueOf(error.getMessage()));
      return new String(RestHttp.errorResponse(500).body(), StandardCharsets.ISO_8859_1);
    }
    return new String(response(x).body(), StandardCharsets.ISO_8859_1);
  }

  // ---- the transport-independent core -----------------------------------------------------------------------
  public Response dispatch(Request request) {
    ensureDispatchReady();
    String target = request.target();
    int mark = target.indexOf('?');
    String rawPath = mark < 0 ? target : target.substring(0, mark);
    String queryText = mark < 0 ? "" : target.substring(mark + 1);
    String path = RestRouter.percentDecode(rawPath, false);
    if (path == null) return RestHttp.errorResponse(400);
    Map<String, String> query = new HashMap<>();
    if (!queryText.isEmpty()) {
      for (String pair : queryText.split("&", -1)) {
        if (pair.isEmpty()) continue;
        int eq = pair.indexOf('=');
        String key = RestRouter.percentDecode(eq < 0 ? pair : pair.substring(0, eq), true);
        String value = RestRouter.percentDecode(eq < 0 ? "" : pair.substring(eq + 1), true);
        if (key == null || value == null) return RestHttp.errorResponse(400);
        query.putIfAbsent(key, value);
      }
    }
    RestRouter.Match match = table.match(request.method(), rawPath);
    if (match.status() == 404) return RestHttp.errorResponse(404);
    if (match.status() == 405) return RestHttp.errorResponse(405, new Header("Allow", match.allow()));
    String annotation = match.route().annotation;
    Integer idx = entryIndex.get(annotation);
    if (idx == null) throw VmException.host("rest: no program is serving (call rest_serve first)");
    Exchange exchange = new Exchange(this, request, match.params(), query, path);
    try {
      caller.call(idx, Value.object("RestExchange", exchange));
    } catch (RuntimeException | StackOverflowError error) {
      onHandlerError.accept(entryName.get(annotation), String.valueOf(error.getMessage()));
      return RestHttp.errorResponse(500);
    }
    return response(exchange);
  }

  private static Response response(Exchange x) {
    List<Header> headers = new ArrayList<>(x.headers.values());
    byte[] body = x.body.getBytes(StandardCharsets.ISO_8859_1);
    if (x.status == 204 || x.status == 304) body = new byte[0];
    else if (body.length > 0 && !x.headers.containsKey("content-type")) headers.add(0, new Header("Content-Type", "text/plain; charset=utf-8"));
    return new Response(x.status, headers, body);
  }

  // ---- the stdio pump ----------------------------------------------------------------------------------------
  private void write(Response response, boolean head) throws IOException {
    output.write(RestHttp.encodeResponse(response, head));
    output.flush();
  }

  private void pump() {
    byte[] buffer = new byte[8192]; int length = 0;
    try {
      while (true) {
        // answer everything already framed before blocking for more input
        while (true) {
          RestHttp.Taken taken = RestHttp.takeRequest(buffer, length, maxBodyBytes);
          if (taken.need()) break;
          if (taken.fatal() != 0) { write(RestHttp.errorResponse(taken.fatal()), false); return; }
          System.arraycopy(buffer, taken.consumed(), buffer, 0, length - taken.consumed());
          length -= taken.consumed();
          write(dispatch(taken.request()), taken.request().method().equals("HEAD"));
          if (stopped) return;
        }
        if (length == buffer.length) buffer = Arrays.copyOf(buffer, (int) Math.min(Integer.MAX_VALUE - 8, buffer.length * 2L));
        int n = input.read(buffer, length, buffer.length - length);
        if (n < 0) return; // EOF: a truncated request is dropped without a response
        length += n;
      }
    } catch (IOException closed) {
      // the peer went away (broken pipe / closed stream): nothing more can be read or answered
    }
  }
}
