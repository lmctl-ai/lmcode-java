package io.lmctl.lmcode.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/**
 * Pins the shared REST conformance suite ({@code conformance/rest-suite}) with the portable tree digest: sha256 over
 * rows {@code "<sha256(file)>  <relative path>\n"}, paths sorted bytewise (UTF-8). Shell equivalent:
 * {@code (cd DIR && find . -type f -printf '%P\n' | LC_ALL=C sort | xargs sha256sum) | sha256sum}.
 * <pre>RestSuitePin --verify | --write-lock COMMIT</pre>
 */
public final class RestSuitePin {
  private RestSuitePin() {}

  static final Path SUITE = Path.of("conformance", "rest-suite"), LOCK = Path.of("rest-suite.lock");
  static final String REPO = "https://github.com/lmctl-ai/lmcode";

  static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }

  static String sha256(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

  public static String digest(Path root) throws Exception {
    Map<byte[], String> rows = new TreeMap<>(Arrays::compareUnsigned);
    try (Stream<Path> files = Files.walk(root)) {
      for (Path p : files.filter(Files::isRegularFile).toList()) {
        String relative = root.relativize(p).toString().replace('\\', '/');
        rows.put(relative.getBytes(StandardCharsets.UTF_8), sha256(Files.readAllBytes(p)) + "  " + relative + "\n");
      }
    }
    return sha256(String.join("", rows.values()).getBytes(StandardCharsets.UTF_8));
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 2 && args[0].equals("--write-lock")) {
      if (!args[1].matches("[0-9a-f]{40}")) throw new IllegalArgumentException("expected an immutable commit SHA");
      Files.writeString(LOCK, "repo=" + REPO + "\ncommit=" + args[1] + "\nsha256=" + digest(SUITE) + "\n");
      System.out.println("wrote " + LOCK);
    } else if (args.length == 1 && args[0].equals("--verify")) {
      Properties lock = new Properties();
      try (var in = Files.newInputStream(LOCK)) { lock.load(in); }
      String have = digest(SUITE);
      if (!have.equals(lock.getProperty("sha256"))) throw new AssertionError("conformance/rest-suite digest " + have + " differs from " + lock.getProperty("sha256"));
      System.out.println("conformance/rest-suite matches " + lock.getProperty("commit"));
    } else {
      System.err.println("usage: RestSuitePin --verify | --write-lock COMMIT");
      System.exit(2);
    }
  }
}
