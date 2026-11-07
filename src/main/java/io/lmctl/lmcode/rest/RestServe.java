package io.lmctl.lmcode.rest;

import io.lmctl.lmcode.Context;
import io.lmctl.lmcode.Program;
import io.lmctl.lmcode.Registry;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves a compiled program's REST routes over stdin/stdout (the {@code rest} std module; no network).
 * <pre>java -cp target/classes io.lmctl.lmcode.rest.RestServe PROGRAM.dslb HOST.schema.json [--max-body-bytes N]</pre>
 * stdout carries only HTTP responses; handler failures go to stderr. Exit 0 after serving, 2 usage, 3 error.
 */
public final class RestServe {
  private RestServe() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2 || (args.length > 2 && (args.length != 4 || !args[2].equals("--max-body-bytes")))) {
      System.err.println("usage: RestServe PROGRAM.dslb HOST.schema.json [--max-body-bytes N]");
      System.exit(2);
    }
    RestOptions options = new RestOptions();
    if (args.length == 4) options.maxBodyBytes(Long.parseLong(args[3]));
    int code = 0;
    try {
      Registry registry = new Registry();
      RestController rest = Rest.registerRest(registry, options);
      Program program = Program.load(Files.readAllBytes(Path.of(args[0])), Files.readAllBytes(Path.of(args[1])), registry);
      rest.run(program, new Context());
    } catch (Exception | StackOverflowError e) {
      System.err.println("rest-serve: " + e.getMessage());
      code = 3;
    }
    System.out.flush();
    System.exit(code);
  }
}
