# Riftmana Value widget

A small Android app with a home-screen widget that shows the current
"Total Value" from the riftmana-tracker feed
(https://mustafacamurcu.github.io/riftmana-tracker/latest.json).

No Play Store listing — this is sideloaded.

## What it does

- Widget polls `latest.json` every 30 minutes via WorkManager (matches the
  hourly scrape cadence with margin), and immediately on add or tap.
- Tapping the small refresh glyph on the widget forces an immediate refetch.
- Tapping the rest of the widget opens the app, which shows the same value
  and a manual "Refresh now" button.
- Last-known value is cached in SharedPreferences, so the widget still shows
  something useful if the network is briefly unavailable.

## Build

Requires a JDK and the Android SDK (this was built with JDK 17 and
compileSdk 34; `local.properties` — not committed — points `sdk.dir` at
your SDK install).

```
cd android-widget
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install on your phone

1. Copy `app-debug.apk` to the phone (email it to yourself, use a cloud
   drive, or `adb install app-debug.apk` over USB with USB debugging
   enabled).
2. On the phone, enable "Install unknown apps" for whichever app you used
   to open the APK (Settings → Apps → Special access → Install unknown
   apps), then open the APK to install.
3. Long-press an empty area of the home screen → **Widgets** → search
   "Riftmana Value" → drag it onto the home screen.

## Notes

- The widget targets `com.riftmana.widget` / applicationId
  `com.riftmana.widget` — change this in `app/build.gradle.kts` if it
  collides with something else on your phone.
- minSdk 26 (Android 8.0+).
- If you ever move the tracker off `mustafacamurcu.github.io`, update the
  URL in `RefreshWorker.kt`.
