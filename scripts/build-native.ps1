$ErrorActionPreference = "Stop"
$nativeImage = Join-Path $env:JAVA_HOME "bin/native-image.cmd"
if (!(Test-Path $nativeImage)) { throw "GraalVM native-image.cmd is required" }
$nativeArgs = @(
  "-H:+UnlockExperimentalVMOptions", "-J-Xmx4g", "-H:NumberOfThreads=2",
  "--no-fallback", "-march=compatibility", "-H:+ReportExceptionStackTraces",
  "-H:IncludeResources=greeter.schema.json", "-H:IncludeResources=notices.txt",
  "-cp", "target/classes", "io.lmctl.lmcode.examples.Greeter", "target/greeter"
)
& $nativeImage @nativeArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (!(Test-Path "target/greeter.exe")) { throw "native executable missing" }
