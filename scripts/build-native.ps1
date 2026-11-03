$ErrorActionPreference = "Stop"
$nativeImage = Join-Path $env:JAVA_HOME "bin/native-image.cmd"
if (!(Test-Path $nativeImage)) { throw "GraalVM native-image.cmd is required" }
$utf8Manifest = (Resolve-Path (Join-Path $PSScriptRoot 'windows-utf8.manifest')).Path
$nativeArgs = @(
  "-H:+UnlockExperimentalVMOptions", "-J-Xmx4g", "-H:NumberOfThreads=2",
  "--no-fallback", "-march=compatibility", "-H:+ReportExceptionStackTraces",
  "-H:IncludeResources=greeter.schema.json", "-H:IncludeResources=notices.txt",
  # Native images have the same narrow Windows argv boundary as the JVM launcher.
  # Keep the real Unicode filename goldens; merge UTF-8 into the generated PE manifest.
  "-H:NativeLinkerOption=/MANIFEST:EMBED", "-H:NativeLinkerOption=/MANIFESTINPUT:$utf8Manifest",
  "-cp", "target/classes", "io.lmctl.lmcode.examples.Greeter", "target/greeter"
)
& $nativeImage @nativeArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (!(Test-Path "target/greeter.exe")) { throw "native executable missing" }
