$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$sdkPlatformTools = "C:\Users\37768\AppData\Local\Android\Sdk\platform-tools"
$apk = Join-Path $root "DanChi-debug.apk"
if (-not (Test-Path $apk)) {
    $apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
}

if (Test-Path $sdkPlatformTools) {
    $env:PATH = "$sdkPlatformTools;$env:PATH"
}

if (-not (Test-Path $apk)) {
    throw "APK not found. Run tools\BuildDebugApk.ps1 first."
}

$devices = adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
if (-not $devices) {
    Write-Host "No Android device found."
    Write-Host "1. Enable Developer options on the phone."
    Write-Host "2. Enable USB debugging."
    Write-Host "3. Connect USB and allow RSA authorization."
    Write-Host "4. Run: adb devices"
    exit 1
}

adb install -r $apk
adb shell monkey -p com.danchi.app.debug 1
Write-Host "Installed and launched DanChi debug build."
