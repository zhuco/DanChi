$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$gradle = Join-Path $env:TEMP "gradle-9.2.1\bin\gradle.bat"

if (Test-Path $jdk) {
    $env:JAVA_HOME = $jdk
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}

Push-Location $root
try {
    if (Test-Path $gradle) {
        & $gradle --no-daemon --console=plain testDebugUnitTest assembleDebug
    } else {
        & (Join-Path $root "gradlew.bat") --no-daemon --console=plain testDebugUnitTest assembleDebug
    }

    $source = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
    $target = Join-Path $root "DanChi-debug.apk"
    Copy-Item -Force $source $target
    Write-Host "APK ready: $target"
} finally {
    Pop-Location
}
