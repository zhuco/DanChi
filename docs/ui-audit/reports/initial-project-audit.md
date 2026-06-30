# DanChi UI Audit Baseline

Date: 2026-06-29

## Project Type

DanChi is a native Android app built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, Coroutines, and Android TextToSpeech.

## Screenshot Strategy

This is not a browser app, so Playwright is not appropriate. The repo now includes `scripts/ui-audit/capture-ui.ps1`, which uses `adb screencap` when an Android device or emulator is connected.

Current blocker: `adb devices` returned no connected devices during this audit, so current runtime screenshots cannot be produced in this environment yet.

## Target Screenshot Names

1. `01-home.png`
2. `02-plan-settings.png`
3. `03-mode-select.png`
4. `04-firm-preview.png`
5. `05-firm-choice.png`
6. `06-word-detail.png`
7. `07-remember-forget.png`
8. `08-review.png`
9. `09-day-complete.png`
10. `10-settings.png`
11. `11-empty-state.png`
12. `12-error-state.png`

## Baseline Verification

- Unit tests: passed with `%TEMP%\gradle-9.2.1\bin\gradle.bat --no-daemon testDebugUnitTest`
- Debug build: passed with `%TEMP%\gradle-9.2.1\bin\gradle.bat --no-daemon assembleDebug`
- Gradle wrapper note: `.\\gradlew.bat` hit a wrapper zip lock in the local Gradle cache, so the pre-extracted Gradle path was used.
