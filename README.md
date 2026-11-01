# Orbit Clock

Native Android phone and Wear OS clock apps with a Material You / Material 3 interface, shared alarms, and rotating-bezel controls for Galaxy Watch 4 Classic.

Both debug APKs now compile successfully. The project passes 7 Kotlin/JUnit tests and 44 standalone checks of the production timing and gesture policies. Android lint reports no errors. The redesigned phone UI also passes 24 Android 15 emulator interaction checks. **The [Material UI interaction suite](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41176288860) passes alarm editing/persistence, city search, timers, stopwatch, bedtime settings, dark mode, and 130% text size. Physical-device testing remains.** This is a working build of an initial prototype, not full Google Clock feature parity or a validated alarm replacement.

Private repository: [vish9731-agentic/orbit-clock](https://github.com/vish9731-agentic/orbit-clock).

## Material You design

- Genuine Material 3 components with wallpaper-derived dynamic colors on supported Android 12+ devices and light/dark fallback themes.
- Icon navigation, a floating add action, spacious alarm cards, large time typography and a quieter permission panel.
- Bottom-sheet alarm editor, Material time picker, outlined fields, weekday selection and Material switches.
- Redesigned clock, timer, stopwatch, bedtime and dark ringing screens; compact watch time controls and weekday rows.
- System-bar and keyboard insets handled explicitly; header/navigation checked at 130% font scale.

## What is implemented

- Alarm creation/editing, labels, weekday repeats, enable/disable, sound/vibration and configurable snooze.
- Independent local alarm scheduling on phone and watch; persisted records and reboot/time/timezone restoration.
- Wear Data Layer synchronization of alarms and connected snooze/dismiss changes, including deletion records.
- Ringing screen with touch controls, sound, vibration and notifications. Rotary input accumulates to snooze or dismiss; direction and thresholds require testing on the actual watch.
- World clocks, minute-based timers, stopwatch with laps, and a basic bedtime sleep goal/wake-up schedule.
- Native platform views with a compact watch layout. Both apps use application ID `dev.orbit.clock` and the same development signing key.

This app owns its alarms. It does not read or modify Google Clock's private alarm database. Music-service integrations, Assistant routines, sleep tracking, bedtime reminders, watch hardware validation and further accessibility tuning remain unfinished. World-clock choices, stopwatch state and sleep goals are local to each device. Offline dismissal cannot immediately stop the other device, and synchronization is eventually consistent.

## Ringing snooze adjustment

The ringing screen has a minimal **− / minutes / +** control above Snooze. Each tap changes the duration by five minutes, with a five-minute minimum and sixty-minute maximum; the corresponding button disables at each limit. Selection survives screen recreation and applies to this ringing occurrence, without editing the alarm's saved default. Touch Snooze and rotary snooze use the same selection. Dismiss is unchanged.

[Build/tests/lint/publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41196995849) and [24 emulator UI checks](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41196995861) passed for release 0.1.14. Real one-minute timers were delivered, then the selected 25-minute snooze deadline and separate dismissal were verified.

## Build and test

Use JDK 17, Android SDK Platform 35 and Gradle 8.9. A Gradle wrapper is included. Configure your SDK path with `ANDROID_HOME` or Android Studio's generated `local.properties`.

```bash
bash tools/test-core.sh
./gradlew :core:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug
```

The verified local outputs are:

- `phone/build/outputs/apk/debug/phone-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

Install the phone APK on the phone and the wear APK on the watch. Android will ask you to allow installation from the download/browser app. Watch installation requires separate Wear OS tooling or wireless ADB; downloading the watch APK to a phone does not install it on the watch.

Pair the watch using Galaxy Wearable, open both apps, and grant precise-alarm and notification permissions. On Android 14+, allow the ringing screen/full-screen intent if requested. Confirm an alarm appears on both devices before testing it. A debug APK is installable with debugging enabled; it does not automatically attach a live debugger.

## Request changes and builds from a phone

The intended flow is: request a change → a coding session with access updates this repository → GitHub Actions builds/tests it → download the successful APKs. Your Mac does not need to remain running for GitHub-hosted builds. The coding session must have repository write access and the ability to inspect Actions results; signing into ChatGPT alone does not provide those capabilities.

The [Android debug workflow](.github/workflows/android-debug.yml) runs on push, pull request or manual dispatch. After a successful default-branch build, it is configured to publish individual APKs to the `debug-latest` prerelease as well as Actions artifacts. **Verified:** [cloud build, tests, lint and publication succeeded](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41104668417). Both phone and watch debug APKs are built and uploaded from the verified revision; the development signing key is unchanged.

Verified download links (version `0.1.17`, version code `18` for this build):

- [Phone debug APK](https://github.com/vish9731-agentic/orbit-clock/releases/download/debug-latest/orbit-clock-phone-debug.apk)
- [Watch debug APK](https://github.com/vish9731-agentic/orbit-clock/releases/download/debug-latest/orbit-clock-watch-debug.apk)

Private downloads require GitHub authentication. To trigger a build yourself, open the repository's Actions tab, choose **Android debug builds and tests**, then **Run workflow**. Only treat a build as available after the run succeeds and its APK assets exist.

The checked-in `debug.keystore` is deliberately shared **development-only** material, using the standard `android` passwords. It keeps phone/watch signing compatible and allows later debug updates to retain app data. Never use this key for production releases. CI uses the workflow run number for increasing version codes; retain the same repository/workflow and key for subsequent debug updates.

## Verification and remaining device checks

See [verification/STATUS.md](verification/STATUS.md) for the completed build/test evidence. Before relying on the app, test:

- Alarm delivery with locked screens, Doze, process death, reboot and timezone changes.
- Create/edit/delete and snooze/dismiss from both devices; offline changes and reconnection.
- Physical bezel direction, thresholds, focus, accidental movement and touch fallback on Watch 4 Classic.
- Concurrent alarms/timers, permission revocation, timeout, and signed APK updates.

OS policy controls full-screen presentation, and force-stopping an app prevents alarm delivery until it is opened again. Compilation and policy tests do not establish on-device reliability.

## Volume-button snooze

While the ringing screen is open and focused, either physical volume button snoozes on release using the selected duration. Held/repeated presses are consumed; other screens retain normal volume behavior. This shortcut does not intercept volume keys globally when only a notification is visible.
