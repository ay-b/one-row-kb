# Crash reports

How a user's crash reaches us without the app having internet access.

1. **Capture.** `OneRowApp` installs an uncaught-exception handler (`report/CrashReport.kt`). On a crash it writes ONE file,
   `files/last_crash.txt` in device-protected storage, then lets Android continue as usual (crash dialog, Play vitals).
2. **What is in the report** (and nothing else): app version and code, Android version and SDK, phone manufacturer and model, system
   language, the crash time (UTC), and the stack trace as class names and code locations. Exception **messages are never included**
   (they can quote what the user typed). `CrashReportTest` proves it.
3. **Shake.** While the keyboard is on screen (and "Shake the phone to see the crash report" is on in Settings) three hard jolts open
   `ReportActivity`: explainer, the first 300 characters of the report, "Show all", **Send by email**, Close. Settings has the same
   screen behind the "Crash report" button. With no crash saved it says so and offers no Send.
4. **Send.** `Intent.ACTION_SENDTO mailto:` to `apps@y-b.one`, subject "OneRow crash report", the whole report as the body. The user
   presses send in their mail app. The app itself still has no internet permission.

## Reading a report

Release builds are shrunk by R8, so the class names are scrambled. Keep the mapping file of EVERY build you hand out
(`app/build/outputs/mapping/release/mapping.txt`, copied to `releases/onerow-<version>-mapping.txt` when building). Then:

```bash
/opt/homebrew/share/android-commandlinetools/cmdline-tools/latest/bin/retrace releases/onerow-0.25.0-mapping.txt trace.txt
```

(`trace.txt` = the lines of the report from the first exception line on.) Play Console uploads the mapping with the bundle too, so
Android vitals shows readable traces for users who share diagnostics.
