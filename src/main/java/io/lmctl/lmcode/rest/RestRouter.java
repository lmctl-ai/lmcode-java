package io.lmctl.lmcode.rest;

import io.lmctl.lmcode.VmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Route templates and matching for the opt-in {@code rest} std module (spec/std-rest.md, "Route templates").
 * Every string here is an lmcode byte string held as ISO-8859-1 text (one char per byte).
 */
public final class RestRouter {
  private RestRouter() {}

  public static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
  private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern PLACEHOLDER = Pattern.compile("^\\{(.*?)(\\.\\.\\.)?\\}\\z", Pattern.DOTALL);

  public enum Kind { LITERAL, PARAM, REST }

  public record Segment(Kind kind, String value) {}

  public static final class Route {
    public final String method, path, shape;
    public final List<Segment> segments;
    public String annotation;
    Route(String method, String path, List<Segment> segments, String shape) {
      this.method = method; this.path = path; this.segments = segments; this.shape = shape;
    }
  }

  /** Result of {@link RouteTable#match}: a route, or status 404, or status 405 with an Allow value. */
  public record Match(Route route, Map<String, String> params, int status, String allow) {
    public boolean found() { return route != null; }
  }

  static VmException fail(String message) { return VmException.host(message); }

  /** Percent-decode a byte string; null on an invalid escape. {@code plus} turns '+' into a space (queries). */
  public static String percentDecode(String text, boolean plus) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '%') {
        if (i + 2 >= text.length()) return null;
        int hi = Character.digit(text.charAt(i + 1), 16), lo = Character.digit(text.charAt(i + 2), 16);
        if (hi < 0 || lo < 0) return null;
        out.append((char) (hi * 16 + lo));
        i += 2;
      } else if (plus && c == '+') out.append(' ');
      else out.append(c);
    }
    return out.toString();
  }

  public static Route parseRoute(String method, String path) {
    String label = method + " " + path;
    if (!METHODS.contains(method)) throw invalid(label, "method must be one of " + String.join(", ", METHODS));
    if (!path.startsWith("/")) throw invalid(label, "path must start with '/'");
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (c <= 0x20 || c == 0x7f || c == '?' || c == '#')
        throw invalid(label, "path must not contain whitespace, control characters, ? or #");
    }
    String[] raw = path.substring(1).split("/", -1);
    Set<String> names = new HashSet<>();
    List<Segment> segments = new ArrayList<>();
    StringBuilder shape = new StringBuilder();
    for (int index = 0; index < raw.length; index++) {
      String text = raw[index];
      boolean last = index == raw.length - 1;
      if (text.isEmpty() && !last) throw invalid(label, "empty segment ('//')");
      var m = PLACEHOLDER.matcher(text);
      Segment segment;
      if (!m.find()) {
        if (text.indexOf('{') >= 0 || text.indexOf('}') >= 0) throw invalid(label, "malformed placeholder in segment '" + text + "'");
        segment = new Segment(Kind.LITERAL, text);
      } else {
        String name = m.group(1);
        boolean rest = m.group(2) != null;
        if (!NAME.matcher(name).matches()) throw invalid(label, "invalid parameter name '" + name + "'");
        if (!names.add(name)) throw invalid(label, "duplicate parameter '" + name + "'");
        if (rest && !last) throw invalid(label, "'{" + name + "...}' must be the last segment");
        segment = new Segment(rest ? Kind.REST : Kind.PARAM, name);
      }
      segments.add(segment);
      if (index > 0) shape.append('/');
      shape.append(segment.kind() == Kind.LITERAL ? "L" + segment.value() : segment.kind() == Kind.PARAM ? "P" : "R");
    }
    return new Route(method, path, List.copyOf(segments), shape.toString());
  }

  private static VmException invalid(String label, String reason) { return fail("rest: invalid route " + label + ": " + reason); }

  private static int rank(Kind kind) { return kind.ordinal(); }

  /** Negative when a is more specific than b (segment by segment, literal < {p} < {p...}). */
  private static int compareSpecificity(Route a, Route b) {
    int n = Math.min(a.segments.size(), b.segments.size());
    for (int i = 0; i < n; i++) {
      int d = rank(a.segments.get(i).kind()) - rank(b.segments.get(i).kind());
      if (d != 0) return d;
    }
    return a.segments.size() - b.segments.size();
  }

  private static Map<String, String> matchSegments(Route route, String[] raw) {
    Map<String, String> params = new HashMap<>();
    List<Segment> segs = route.segments;
    for (int i = 0; i < segs.size(); i++) {
      Segment s = segs.get(i);
      if (s.kind() == Kind.REST) {
        if (raw.length <= i) return null;
        StringJoiner parts = new StringJoiner("/");
        for (int j = i; j < raw.length; j++) {
          String d = percentDecode(raw[j], false);
          if (d == null) return null;
          parts.add(d);
        }
        params.put(s.value(), parts.toString());
        return params;
      }
      if (i >= raw.length) return null;
      String decoded = percentDecode(raw[i], false);
      if (decoded == null) return null;
      if (s.kind() == Kind.LITERAL) { if (!decoded.equals(s.value())) return null; }
      else { if (decoded.isEmpty()) return null; params.put(s.value(), decoded); }
    }
    return raw.length == segs.size() ? params : null;
  }

  public static final class RouteTable {
    private final List<Route> routes = new ArrayList<>();
    public List<Route> routes() { return Collections.unmodifiableList(routes); }
    public int size() { return routes.size(); }

    public Route add(String method, String path, String annotation) {
      Route route = parseRoute(method, path);
      for (Route r : routes) if (r.method.equals(method) && r.shape.equals(route.shape)) throw fail("rest: duplicate route " + method + " " + path);
      route.annotation = annotation;
      routes.add(route);
      return route;
    }

    /** {@code rawPath} is the undecoded request path without the query. */
    public Match match(String method, String rawPath) {
      String[] raw = rawPath.substring(1).split("/", -1);
      List<Match> hits = new ArrayList<>();
      for (Route route : routes) {
        Map<String, String> params = matchSegments(route, raw);
        if (params != null) hits.add(new Match(route, params, 0, null));
      }
      if (hits.isEmpty()) return new Match(null, null, 404, null);
      Match direct = best(hits, method);
      if (direct == null && method.equals("HEAD")) direct = best(hits, "GET");
      if (direct != null) return direct;
      TreeSet<String> allow = new TreeSet<>();
      for (Match h : hits) allow.add(h.route().method);
      if (allow.contains("GET")) allow.add("HEAD");
      return new Match(null, null, 405, String.join(", ", allow));
    }

    private static Match best(List<Match> hits, String verb) {
      return hits.stream().filter(h -> h.route().method.equals(verb)).min((x, y) -> compareSpecificity(x.route(), y.route())).orElse(null);
    }
  }
}
