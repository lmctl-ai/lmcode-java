package io.lmctl.lmcode.rest;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The HTTP/1.1 subset of spec/std-rest.md: request framing and response encoding. Bytes travel as ISO-8859-1 text
 * so lmcode byte strings round-trip untouched. Whitespace is ASCII only (0x00-0x20, 0x7f).
 */
public final class RestHttp {
  private RestHttp() {}

  public static final int MAX_HEADER_BYTES = 16 * 1024;

  public record Header(String name, String value) {}
  public record Request(String method, String target, List<Header> headers, byte[] body) {}
  public record Response(int status, List<Header> headers, byte[] body) {}
  /** Parsed head, or {@code error} != 0 (an HTTP status). */
  public record Head(String method, String target, List<Header> headers, int error) {}
  /** Content-Length (or 0), or {@code error} != 0. */
  public record BodyLength(long length, int error) {}
  /** One framing step: {@code need} more input, a {@code fatal} status, or a request with the bytes it {@code consumed}. */
  public record Taken(boolean need, int fatal, Request request, int consumed) {}

  private static final Map<Integer, String> REASONS = Map.ofEntries(
    Map.entry(200, "OK"), Map.entry(201, "Created"), Map.entry(202, "Accepted"), Map.entry(204, "No Content"),
    Map.entry(301, "Moved Permanently"), Map.entry(302, "Found"), Map.entry(304, "Not Modified"), Map.entry(400, "Bad Request"),
    Map.entry(401, "Unauthorized"), Map.entry(403, "Forbidden"), Map.entry(404, "Not Found"), Map.entry(405, "Method Not Allowed"),
    Map.entry(408, "Request Timeout"), Map.entry(409, "Conflict"), Map.entry(413, "Payload Too Large"),
    Map.entry(415, "Unsupported Media Type"), Map.entry(422, "Unprocessable Entity"), Map.entry(429, "Too Many Requests"),
    Map.entry(431, "Request Header Fields Too Large"), Map.entry(500, "Internal Server Error"), Map.entry(501, "Not Implemented"),
    Map.entry(502, "Bad Gateway"), Map.entry(503, "Service Unavailable"));

  public static String reasonPhrase(int status) {
    String known = REASONS.get(status);
    if (known != null) return known;
    return status < 300 ? "OK" : status < 400 ? "Redirect" : status < 500 ? "Client Error" : "Server Error";
  }

  private static final Map<Integer, String> ERRORS = Map.of(
    400, "{\"error\":\"bad request\"}", 404, "{\"error\":\"not found\"}", 405, "{\"error\":\"method not allowed\"}",
    413, "{\"error\":\"payload too large\"}", 431, "{\"error\":\"request header too large\"}",
    500, "{\"error\":\"internal error\"}", 501, "{\"error\":\"not implemented\"}");

  public static Response errorResponse(int status, Header... extra) {
    List<Header> headers = new ArrayList<>();
    headers.add(new Header("Content-Type", "application/json"));
    headers.addAll(Arrays.asList(extra));
    return new Response(status, headers, ERRORS.get(status).getBytes(StandardCharsets.ISO_8859_1));
  }

  static String lower(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); b.append(c >= 'A' && c <= 'Z' ? (char) (c + 32) : c); }
    return b.toString();
  }

  private static Head bad() { return new Head(null, null, null, 400); }

  /** Parse the header section (without the terminating blank line). */
  public static Head parseHead(String text) {
    String[] lines = text.split("\r\n", -1);
    String first = lines[0];
    int sp1 = first.indexOf(' ');
    if (sp1 <= 0) return bad();
    String method = first.substring(0, sp1);
    for (int i = 0; i < method.length(); i++) if (method.charAt(i) < 'A' || method.charAt(i) > 'Z') return bad();
    int sp2 = first.indexOf(' ', sp1 + 1);
    if (sp2 <= sp1 + 1) return bad();
    String target = first.substring(sp1 + 1, sp2);
    for (int i = 0; i < target.length(); i++) if (target.charAt(i) <= 0x20 || target.charAt(i) == 0x7f) return bad();
    String version = first.substring(sp2 + 1);
    if (!version.equals("HTTP/1.0") && !version.equals("HTTP/1.1")) return bad();
    if (target.charAt(0) != '/' || target.indexOf('#') >= 0) return bad();
    List<Header> headers = new ArrayList<>();
    for (int n = 1; n < lines.length; n++) {
      String line = lines[n];
      int colon = line.indexOf(':');
      if (colon <= 0) return bad();
      for (int i = 0; i < colon; i++) if (line.charAt(i) <= 0x20 || line.charAt(i) == 0x7f) return bad();
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if ((c <= 0x08) || (c >= 0x0a && c <= 0x1f) || c == 0x7f) return bad();
      }
      int a = colon + 1, b = line.length();
      while (a < b && (line.charAt(a) == ' ' || line.charAt(a) == '\t')) a++;
      while (b > a && (line.charAt(b - 1) == ' ' || line.charAt(b - 1) == '\t')) b--;
      headers.add(new Header(line.substring(0, colon), line.substring(a, b)));
    }
    return new Head(method, target, List.copyOf(headers), 0);
  }

  /** Content-Length (or 0); Transfer-Encoding is unsupported (501). */
  public static BodyLength bodyLength(List<Header> headers) {
    for (Header h : headers) if (lower(h.name()).equals("transfer-encoding")) return new BodyLength(0, 501);
    List<String> values = new ArrayList<>();
    for (Header h : headers) if (lower(h.name()).equals("content-length")) values.add(h.value());
    if (values.isEmpty()) return new BodyLength(0, 0);
    if (new HashSet<>(values).size() > 1) return new BodyLength(0, 400);
    String v = values.get(0);
    if (v.isEmpty() || v.length() > 15) return new BodyLength(0, 400);
    for (int i = 0; i < v.length(); i++) if (v.charAt(i) < '0' || v.charAt(i) > '9') return new BodyLength(0, 400);
    return new BodyLength(Long.parseLong(v), 0);
  }

  public static byte[] encodeResponse(Response response, boolean head) {
    StringBuilder text = new StringBuilder("HTTP/1.1 ").append(response.status()).append(' ').append(reasonPhrase(response.status())).append("\r\n");
    for (Header h : response.headers()) text.append(h.name()).append(": ").append(h.value()).append("\r\n");
    text.append("Content-Length: ").append(response.body().length).append("\r\n\r\n");
    byte[] prefix = text.toString().getBytes(StandardCharsets.ISO_8859_1);
    if (head) return prefix;
    byte[] out = Arrays.copyOf(prefix, prefix.length + response.body().length);
    System.arraycopy(response.body(), 0, out, prefix.length, response.body().length);
    return out;
  }

  private static int indexOfBlankLine(byte[] b, int len) {
    for (int i = 0; i + 3 < len; i++) if (b[i] == '\r' && b[i + 1] == '\n' && b[i + 2] == '\r' && b[i + 3] == '\n') return i;
    return -1;
  }

  /** Take one request off the front of {@code buffer[0..len)}. A fatal framing error ends serving. */
  public static Taken takeRequest(byte[] buffer, int len, long maxBodyBytes) {
    int end = indexOfBlankLine(buffer, len);
    if (end < 0) return len > MAX_HEADER_BYTES ? fatal(431) : new Taken(true, 0, null, 0);
    if (end > MAX_HEADER_BYTES) return fatal(431);
    Head head = parseHead(new String(buffer, 0, end, StandardCharsets.ISO_8859_1));
    if (head.error() != 0) return fatal(head.error());
    BodyLength size = bodyLength(head.headers());
    if (size.error() != 0) return fatal(size.error());
    if (size.length() > maxBodyBytes) return fatal(413);
    long total = end + 4L + size.length();
    if (len < total) return new Taken(true, 0, null, 0);
    byte[] body = Arrays.copyOfRange(buffer, end + 4, (int) total);
    return new Taken(false, 0, new Request(head.method(), head.target(), head.headers(), body), (int) total);
  }

  private static Taken fatal(int status) { return new Taken(false, status, null, 0); }
}
