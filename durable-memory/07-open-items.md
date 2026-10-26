# Open items and constraints

No Maven Central deployment is requested: build/install locally or vendor sources. Released native greeters need no JVM. Linux requires glibc>=2.35. Windows requires Microsoft Visual C++ x64 runtime DLLs; those platform components are not bundled. Native executables include GraalVM/OpenJDK runtime code under GPLv2 with Classpath Exception, with full text and additional notices embedded. Application/SDK sources remain MIT.

The runtime is not a sandbox. The manifest marks a few libm results with numeric tolerance; the runner applies exactly the reference comparison contract and never rewrites vectors.

- **`Reports.escape(VmString)` is not byte-exact for invalid UTF-8.** It calls `VmString.toString()`, which replaces each
  invalid byte with U+FFFD, so a result or error carrying byte `0xff` prints `EF BF BD`. Probe: `new VmString(new byte[]{(byte)0xff, 'x'})`
  escapes to `"�x"`. The Scala sample writes its own byte-level escaper (`shop.Protocol.jsonString`) that copies bytes unchanged,
  matching the C++ reference; a byte-exact `Reports` escaper would make the Java reference match without a port-specific helper.
