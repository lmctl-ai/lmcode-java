# Configure only the fresh GitHub runner's downloaded JVM launcher, never a user install.
# JDK21's Windows launcher converts argv through CP_ACP before Java sees it:
# https://github.com/openjdk/jdk21u/blob/master/src/java.base/share/native/launcher/main.c
# A process manifest makes CP_ACP UTF-8 without changing machine locale/registry:
# https://learn.microsoft.com/windows/apps/design/globalizing/use-utf8-code-page
# Java -Dfile.encoding and console chcp cannot recover characters already lost there.
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if ($env:RUNNER_OS -ne 'Windows' -or -not $env:JAVA_HOME -or -not $env:RUNNER_TEMP) {
    throw 'This setup is restricted to an isolated Windows Actions runner with JAVA_HOME'
}
$javaBin = Join-Path $env:JAVA_HOME 'bin'
$javaExe = Join-Path $javaBin 'java.exe'
$env:Path = $javaBin + ';' + $env:Path
if ($env:GITHUB_PATH) { Add-Content -LiteralPath $env:GITHUB_PATH -Value $javaBin -Encoding utf8 }
$probeSource = Join-Path $PSScriptRoot 'LauncherUtf8Probe.java'
$argument = ([char]0x6c34).ToString() + ([char]0x00e9).ToString() + [char]::ConvertFromUtf32(0x1f600)
& $javaExe $probeSource $argument
if ($LASTEXITCODE -eq 0) {
    Write-Host 'JVM launcher already preserves the required Unicode argv'
    return
}
Write-Host 'Baseline JVM argv probe failed; enabling the per-process UTF-8 code page'
$mtCommand = Get-Command mt.exe -ErrorAction SilentlyContinue
if ($mtCommand) { $mtExe = $mtCommand.Source }
else {
    $sdkBin = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits/10/bin'
    $mtExe = Get-ChildItem -LiteralPath $sdkBin -Directory |
        Where-Object { $_.Name -match '^10\.[0-9.]+$' } |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'x64/mt.exe' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $mtExe) { throw 'Windows SDK manifest tool mt.exe is required' }
$work = Join-Path $env:RUNNER_TEMP ('lmcode-java-utf8-' + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $work | Out-Null
try {
    $manifest = Join-Path $work 'java.manifest'
    & $mtExe -nologo ('-inputresource:' + $javaExe + ';#1') ('-out:' + $manifest)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the JVM launcher manifest' }
    [xml]$document = Get-Content -LiteralPath $manifest -Raw
    $ns = [System.Xml.XmlNamespaceManager]::new($document.NameTable)
    $ns.AddNamespace('app', 'urn:schemas-microsoft-com:asm.v3')
    $ns.AddNamespace('ws', 'http://schemas.microsoft.com/SMI/2019/WindowsSettings')
    $application = $document.DocumentElement.SelectSingleNode('app:application', $ns)
    if (-not $application) {
        $application = $document.CreateElement('application', 'urn:schemas-microsoft-com:asm.v3')
        $document.DocumentElement.AppendChild($application) | Out-Null
    }
    $settings = $application.SelectSingleNode('app:windowsSettings', $ns)
    if (-not $settings) {
        $settings = $document.CreateElement('windowsSettings', 'urn:schemas-microsoft-com:asm.v3')
        $application.AppendChild($settings) | Out-Null
    }
    $codePage = $settings.SelectSingleNode('ws:activeCodePage', $ns)
    if (-not $codePage) {
        $codePage = $document.CreateElement('activeCodePage', 'http://schemas.microsoft.com/SMI/2019/WindowsSettings')
        $settings.AppendChild($codePage) | Out-Null
    }
    $codePage.InnerText = 'UTF-8'
    $document.Save($manifest)
    & $mtExe -nologo -manifest $manifest ('-outputresource:' + $javaExe + ';#1')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot configure the JVM launcher UTF-8 manifest' }
    & $javaExe $probeSource $argument
    if ($LASTEXITCODE -ne 0) { throw 'JVM Unicode argv still fails after manifest configuration' }
} finally {
    Remove-Item -LiteralPath $work -Recurse -Force
}
