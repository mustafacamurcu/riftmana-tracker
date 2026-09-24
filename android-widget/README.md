# Riftmana Value widget

A small Android app with a home-screen widget that shows the current
"Total Value" from the riftmana-tracker feed
(https://mustafacamurcu.github.io/riftmana-tracker/latest.json), the
change vs. the last hourly reading (green/red), and updates itself.

No Play Store listing — this is sideloaded.

## What it does

- Widget polls `latest.json` every 30 minutes via WorkManager (matches the
  hourly scrape cadence with margin), and immediately on add or tap.
- Shows the delta vs. the previous *hourly* reading (not just the previous
  poll), colored green for an increase, red for a decrease. The baseline
  only shifts when `latest.json`'s `updated_at` actually changes, so it
  doesn't reset every 30-minute background check.
- Tapping the small refresh glyph on the widget forces an immediate refetch.
- Tapping the rest of the widget opens the app, which shows the same value
  and a manual "Refresh now" button.
- Last-known value is cached in SharedPreferences, so the widget still shows
  something useful if the network is briefly unavailable.
- **Self-updating**: on open, and on each background refresh, the app checks
  `releases/version.json`. If a newer `versionCode` is published, it
  downloads `releases/riftmana-widget.apk` and launches Android's install
  prompt — one tap to confirm (Android has no fully silent install path for
  a sideloaded app). If "install from this source" hasn't been granted yet,
  it posts a notification instead of failing silently.

## Build

Requires a JDK and the Android SDK (this was built with JDK 17 and
compileSdk 34; `local.properties` — not committed — points `sdk.dir` at
your SDK install).

```
cd android-widget
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Shipping an update

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Make your changes.
3. `powershell -ExecutionPolicy Bypass -File ..\scripts\publish_widget_update.ps1 -Notes "What changed"`
   — builds, copies the APK to `../releases/`, writes `version.json`, commits,
   and pushes. Installed apps pick it up on their next check.

## Install on your phone (only needed once, for the first self-updating build)

1. Copy `app-debug.apk` to the phone (email it to yourself, use a cloud
   drive, or `adb install app-debug.apk` over USB with USB debugging
   enabled).
2. On the phone, enable "Install unknown apps" for whichever app you used
   to open the APK (Settings → Apps → Special access → Install unknown
   apps), then open the APK to install.
3. Long-press an empty area of the home screen → **Widgets** → search
   "Riftmana Value" → drag it onto the home screen.

After this first install, updates arrive through the app itself — no more
manual file transfers.

## Notes

- The widget targets `com.riftmana.widget` / applicationId
  `com.riftmana.widget` — change this in `app/build.gradle.kts` if it
  collides with something else on your phone.
- minSdk 26 (Android 8.0+).
- All builds are signed with the local debug keystore
  (`~/.android/debug.keystore`), which is what makes self-updates install
  as *updates* rather than requiring uninstall/reinstall — a different
  signature would break that.
- If you ever move the tracker off `mustafacamurcu.github.io`, update the
  URLs in `RefreshWorker.kt` and `UpdateChecker.kt`, and
  `apkUrl`/`versionCode` logic in `scripts/publish_widget_update.ps1`.
- Google Play Protect may show an "App scan recommended" / "app hasn't been
  seen before" prompt on install, even for updates — that's normal for
  sideloaded apps, not an error.
