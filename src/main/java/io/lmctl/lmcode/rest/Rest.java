package io.lmctl.lmcode.rest;

import io.lmctl.lmcode.Registry;
import io.lmctl.lmcode.Stdlib;
import io.lmctl.lmcode.Value;
import io.lmctl.lmcode.VmException;
import io.lmctl.lmcode.VmString;
import io.lmctl.lmcode.rest.RestController.Exchange;
import io.lmctl.lmcode.rest.RestHttp.Header;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/** Registration of the {@code lmcode_std_rest_v1} module (spec/std-rest.md). */
public final class Rest {
  private Rest() {}

  private static final Set<String> RESERVED = Set.of("content-length", "connection", "transfer-encoding");
  private static final List<String> NONE = List.of(), S = List.of("string"), SS = List.of("string", "string");

  private static String str(Value v) { return new String(v.asString().bytes(), StandardCharsets.ISO_8859_1); }
  private static Value text(String s) { return Value.ofString(new VmString(s.getBytes(StandardCharsets.ISO_8859_1))); }
  private static Exchange exchange(Value self) {
    if (self == null || self.kind() != Value.Kind.OBJ || !(self.asObject() instanceof Exchange x)) throw VmException.host("invalid exchange");
    return x;
  }

  public static RestController registerRest(Registry registry) { return registerRest(registry, new RestOptions()); }

  /** Registers the 20 {@code rest} signatures in {@code registry} and returns the controller that owns serving. */
  public static RestController registerRest(Registry registry, RestOptions options) {
    RestController c = new RestController(registry, options);
    registry.beginCompatibilityRegistration();
    try {
      registry.beforeBodyRun = c::beforeBodyRun;
      registry.afterBodyRun = c::finish;
      registry.function("rest_config", SS, "void", (ctx, self, a) -> { c.config(str(a[0]), str(a[1])); return Value.none(); }, Registry.dry((ctx, self, a) -> Value.none()));
      registry.function("rest_route", List.of("string", "string", "string"), "void", (ctx, self, a) -> { c.route(str(a[0]), str(a[1]), str(a[2])); return Value.none(); }, Registry.dry((ctx, self, a) -> Value.none()));
      registry.function("rest_serve", NONE, "void", (ctx, self, a) -> { c.serve(); return Value.none(); }, Registry.dry((ctx, self, a) -> Value.none()));
      registry.function("rest_stop", NONE, "void", (ctx, self, a) -> { c.stop(); return Value.none(); }, Registry.dry((ctx, self, a) -> Value.none()));
      registry.function("rest_cli", List.of("StrList"), "string", (ctx, self, a) -> {
        List<String> argv = Stdlib.collectionValues(a[0]).stream().map(Rest::str).toList();
        return text(c.cli(argv));
      }, Registry.dry((ctx, self, a) -> text("")));

      String t = "RestExchange";
      java.util.function.BiConsumer<String, Registry.Callback> mNoneStr = (name, cb) -> registry.method(t, name, NONE, "string", cb, Registry.dry(cb));
      java.util.function.BiConsumer<String, Registry.Callback> mSStr = (name, cb) -> registry.method(t, name, S, "string", cb, Registry.dry(cb));
      java.util.function.BiConsumer<String, Registry.Callback> mSBool = (name, cb) -> registry.method(t, name, S, "bool", cb, Registry.dry(cb));

      mNoneStr.accept("method", (ctx, self, a) -> text(exchange(self).request.method()));
      mNoneStr.accept("path", (ctx, self, a) -> text(exchange(self).path));
      mSStr.accept("query", (ctx, self, a) -> text(exchange(self).query.getOrDefault(str(a[0]), "")));
      mSBool.accept("has_query", (ctx, self, a) -> Value.ofBool(exchange(self).query.containsKey(str(a[0]))));
      mSStr.accept("param", (ctx, self, a) -> text(exchange(self).params.getOrDefault(str(a[0]), "")));
      mSStr.accept("header", (ctx, self, a) -> { String v = exchange(self).requestHeaders(str(a[0])); return text(v == null ? "" : v); });
      mSBool.accept("has_header", (ctx, self, a) -> Value.ofBool(exchange(self).requestHeaders(str(a[0])) != null));
      mNoneStr.accept("body", (ctx, self, a) -> Value.ofString(new VmString(exchange(self).request.body())));
      Registry.Callback statusCb = (ctx, self, a) -> {
        Exchange x = exchange(self); long code = a[0].asInt();
        if (code < 200 || code > 599) throw VmException.host("rest: status " + code + " out of range 200..599");
        x.status = (int) code; return Value.none();
      };
      registry.method(t, "status", List.of("int"), "void", statusCb, Registry.dry(statusCb));
      Registry.Callback setHeaderCb = (ctx, self, a) -> {
        Exchange x = exchange(self); String name = str(a[0]), value = str(a[1]);
        boolean bad = name.isEmpty() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
        for (int i = 0; i < name.length() && !bad; i++) { char ch = name.charAt(i); bad = ch <= 0x20 || ch == 0x7f || ch == ':'; }
        if (bad) throw VmException.host("rest: invalid response header");
        String key = RestHttp.lower(name);
        if (RESERVED.contains(key)) throw VmException.host("rest: response header " + name + " is managed by the server");
        x.headers.put(key, new Header(name, value)); return Value.none();
      };
      registry.method(t, "set_header", SS, "void", setHeaderCb, Registry.dry(setHeaderCb));
      Registry.Callback sendCb = (ctx, self, a) -> { exchange(self).body = str(a[0]); return Value.none(); };
      registry.method(t, "send", S, "void", sendCb, Registry.dry(sendCb));
      Registry.Callback jsonCb = (ctx, self, a) -> {
        Exchange x = exchange(self); x.body = str(a[0]); x.headers.put("content-type", new Header("Content-Type", "application/json")); return Value.none();
      };
      registry.method(t, "json", S, "void", jsonCb, Registry.dry(jsonCb));
      mSStr.accept("get", (ctx, self, a) -> text(exchange(self).controller.values.getOrDefault(str(a[0]), "")));
      Registry.Callback setCb = (ctx, self, a) -> { exchange(self).controller.values.put(str(a[0]), str(a[1])); return Value.none(); };
      registry.method(t, "set", SS, "void", setCb, Registry.dry(setCb));
      Registry.Callback incCb = (ctx, self, a) -> Value.ofInt(exchange(self).controller.counts.merge(str(a[0]), 1L, Long::sum));
      registry.method(t, "increment", S, "int", incCb, Registry.dry(incCb));
    } finally {
      registry.endCompatibilityRegistration();
    }
    return c;
  }
}
