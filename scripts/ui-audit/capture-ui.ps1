param(
    [string]$ApkPath = "app\build\outputs\apk\debug\app-debug.apk",
    [string]$PackageName = "com.danchi.app.debug",
    [string]$ActivityName = "com.danchi.app.MainActivity",
    [string]$OutputDir = "docs\ui-audit\screenshots",
    [switch]$Install
)

$ErrorActionPreference = "Stop"

function Write-StatusReport {
    param(
        [string]$Status,
        [string]$Reason
    )

    $reportDir = "docs\ui-audit\reports"
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
    $reportPath = Join-Path $reportDir "last-capture-status.md"
    $content = @"
# UI Capture Status

- Status: $Status
- Time: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- Package: $PackageName
- Reason: $Reason

## Pages Still Requiring Manual Capture

1. 01-home.png
2. 02-plan-settings.png
3. 03-mode-select.png
4. 04-firm-preview.png
5. 05-firm-choice.png
6. 06-word-detail.png
7. 07-remember-forget.png
8. 08-review.png
9. 09-day-complete.png
10. 10-settings.png
11. 11-empty-state.png
12. 12-error-state.png

## Manual Flow

1. Connect an Android device or emulator with USB debugging enabled.
2. Run adb devices and confirm the device state is device.
3. Run powershell -ExecutionPolicy Bypass -File scripts/ui-audit/capture-ui.ps1 -Install.
4. Navigate through the app and run the script again after each target screen if the page cannot be reached automatically.
"@
    Set-Content -Encoding UTF8 -Path $reportPath -Value $content
    Write-Host "Wrote $reportPath"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adb) {
    Write-StatusReport -Status "blocked" -Reason "adb is not available on PATH."
    throw "adb is not available on PATH."
}

$deviceLines = adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim().Length -gt 0 }
$readyDevices = $deviceLines | Where-Object { $_ -match "\sdevice$" }
if ($readyDevices.Count -eq 0) {
    Write-StatusReport -Status "blocked" -Reason "No Android device or emulator is connected."
    throw "No Android device or emulator is connected. Connect a device, then rerun this script."
}

if ($Install) {
    if (!(Test-Path $ApkPath)) {
        Write-StatusReport -Status "blocked" -Reason "APK not found at $ApkPath."
        throw "APK not found at $ApkPath. Build with Gradle first."
    }
    adb install -r $ApkPath | Write-Host
}

adb shell am force-stop $PackageName | Out-Null
adb shell am start -n "$PackageName/$ActivityName" | Out-Null
Start-Sleep -Seconds 3

$remotePath = "/sdcard/danchi-ui-audit-home.png"
$localHome = Join-Path $OutputDir "01-home.png"
adb shell screencap -p $remotePath | Out-Null
adb pull $remotePath $localHome | Out-Null
adb shell rm $remotePath | Out-Null

Write-StatusReport -Status "partial" -Reason "Captured launch screen. Other screens need manual navigation or future debug deep links."
Write-Host "Captured $localHome"
