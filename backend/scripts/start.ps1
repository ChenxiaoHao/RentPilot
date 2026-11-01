param(
    [ValidateRange(1, 65535)][int]$Port = 8080,
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$backendPath = Split-Path -Parent $PSScriptRoot
$javaPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
Push-Location -LiteralPath $backendPath
try {
    $jarPath = Join-Path $backendPath 'target/rentpilot-backend-0.1.0.jar'
    if ($Build -or -not (Test-Path -LiteralPath $jarPath)) {
        & .\mvnw.cmd -s .mvn/settings.xml -B -ntp verify
        if ($LASTEXITCODE -ne 0) { throw '后端构建失败，请检查上方输出。' }
    }
    # JBR 21 on Windows can fail to create its selector pipe under a long TEMP path.
    $socketPath = Join-Path $backendPath 'data/jvm-sockets'
    New-Item -ItemType Directory -Path $socketPath -Force >$null
    & $javaPath "-Djdk.net.unixdomain.tmpdir=$socketPath" -jar $jarPath "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "后端启动失败，退出码 $LASTEXITCODE。" }
} finally {
    Pop-Location
}
