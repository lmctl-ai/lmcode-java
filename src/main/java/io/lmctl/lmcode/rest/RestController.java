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

  /** Calls entry {@code index} of the bound program (a seam so unit tests need no bytecode). */
  @FunctionalInterface interface EntryCaller { void call(int index, Value exchange); }

  /** Mutable per-request state behind a lmcode {@code RestExchange} value. */
  static final class Exchange {
    final RestController controller; final Request request; final Map<String, String> params, query; final String path;
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
  private final long maxBodyBytes;
  private final BiConsumer<String, String> onHandlerError;
  final Map<String, String> values = new HashMap<>();
  final Map<String, Long> counts = new HashMap<>();
  final RestRouter.RouteTable table = new RestRouter.RouteTable();
  private List<Program.Entry> entries; private EntryCaller caller;
  private final Map<String, Integer> entryIndex = new HashMap<>();
  private final Map<String, String> entryName = new HashMap<>();
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

  /**
   * Binds {@code program}, runs its body, then (if the body called rest_serve and not rest_stop) serves the input
   * stream until EOF, rest_stop() or a framing error.
   */
  public void run(Program program, Context context) {
    this.program = program; this.context = context; this.stopped = false;
    bindEntries(program.entries(), (index, exchange) -> program.call(index, context, registry, List.of(exchange)));
    program.run(context, registry);
    finish();
  }

  /** After the body: serve the streams if requested and not already stopped. */
  void finish() { if (serving && !stopped) pump(); }

  /** Test seam: bind entries and a caller without a {@link Program}. */
  void bindEntries(List<Program.Entry> entries, EntryCaller caller) { this.entries = entries; this.caller = caller; }

  // ---- lmcode-facing operations -----------------------------------------------------------------------------
  void route(String method, String path, String annotation) {
    if (serving) throw VmException.host("rest: routes are frozen once rest_serve has been called");
    table.add(method, path, annotation);
  }

  void serve() {
    if (serving) throw VmException.host("rest: rest_serve already called");
    if (entries == null) throw VmException.host("rest: no program is bound (run the program through the controller)");
    validate();
    serving = true;
  }

  /** Ends serving once the current response is written. */
  public void stop() { stopped = true; }

  private void validate() {
    if (table.size() == 0) throw VmException.host("rest: no routes registered");
    entryIndex.clear(); entryName.clear();
    for (int i = 0; i < entries.size(); i++) {
      Program.Entry e = entries.get(i);
      if (!e.iface().equals(HANDLER_INTERFACE)) continue;
      if (entryIndex.containsKey(e.annotation())) throw VmException.host("rest: duplicate annotation @" + e.annotation());
      entryIndex.put(e.annotation(), i); entryName.put(e.annotation(), e.name());
    }
    for (RestRouter.Route route : table.routes()) {
      if (entryIndex.containsKey(route.annotation)) continue;
      for (Program.Entry e : entries)
        if (e.annotation().equals(route.annotation)) throw VmException.host("rest: entry " + e.name() + " must implement RestHandler(RestExchange) -> void");
      throw VmException.host("rest: route " + route.method + " " + route.path + " references missing annotation @" + route.annotation);
    }
  }

  // ---- the transport-independent core -----------------------------------------------------------------------
  public Response dispatch(Request request) {
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
    Exchange exchange = new Exchange(this, request, match.params(), query, path);
    try {
      caller.call(entryIndex.get(annotation), Value.object("RestExchange", exchange));
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
