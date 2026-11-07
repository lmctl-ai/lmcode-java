package io.lmctl.lmcode.rest;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiConsumer;

/** Host options of the {@code rest} module (not visible to lmcode programs). */
public final class RestOptions {
  public InputStream input = System.in;
  public OutputStream output = System.out;
  public long maxBodyBytes = 1024 * 1024;
  /** Receives the entry's function name and the VM error message; the default writes one line to stderr. */
  public BiConsumer<String, String> onHandlerError = (entry, message) -> System.err.println("rest: handler " + entry + " failed: " + message);

  public RestOptions input(InputStream value) { input = value; return this; }
  public RestOptions output(OutputStream value) { output = value; return this; }
  public RestOptions maxBodyBytes(long value) { maxBodyBytes = value; return this; }
  public RestOptions onHandlerError(BiConsumer<String, String> value) { onHandlerError = value; return this; }
}
