# Open items and constraints

No Maven Central deployment is requested: build/install locally or vendor sources. Released native greeters need no JVM. Linux requires glibc>=2.35. Windows requires Microsoft Visual C++ x64 runtime DLLs; those platform components are not bundled. Native executables include GraalVM/OpenJDK runtime code under GPLv2 with Classpath Exception, with full text and additional notices embedded. Application/SDK sources remain MIT.

The runtime is not a sandbox. The manifest marks a few libm results with numeric tolerance; the runner applies exactly the reference comparison contract and never rewrites vectors.
