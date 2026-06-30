# UI Capture Status

- Status: blocked
- Time: 2026-06-29 22:51:04
- Package: com.danchi.app.debug
- Reason: No Android device or emulator is connected.

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
