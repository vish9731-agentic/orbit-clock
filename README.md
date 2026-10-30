# Orbit Clock — native Android + Wear OS prototype

A Google Clock-inspired replacement, with independent local alarm scheduling on phone and watch. It does **not** read or modify Google Clock alarms. This is an initial source implementation, not a production release or an exact feature clone. No APK has been compiled in the authoring environment: Android SDK, Gradle, emulator and connected devices were unavailable; the network proxy refused connections during the SDK check. Production timing and rotary-gesture components have now been compiled with the available Java compiler module and tested: 30 checks passed. This does not validate Kotlin compilation, Android UI, permissions, Bluetooth delivery or hardware behavior.

## Open and build

1. Open this directory in Android Studio. Use JDK 17 (or the IDE bundled JDK compatible with AGP 8.7.3).
2. Install Android SDK Platform 35 and accept SDK licenses. Allow Android Studio to sync Gradle dependencies. Use Gradle 8.9; this source bundle does not include a generated Gradle wrapper.
3. With Gradle 8.9 installed, run `gradle wrapper --gradle-version 8.9`, then `./gradlew :core:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug`.
4. Install `phone/build/outputs/apk/debug/phone-debug.apk` on the phone and `wear/build/outputs/apk/debug/wear-debug.apk` on the watch. Both apps deliberately share application ID `dev.orbit.clock`. Build/install both using the **same signing key**. Do not install both variants on one device.
5. Pair the Watch 4 Classic using Galaxy Wearable. Google Play services and the Wear OS Data Layer must be available on both devices.
6. Open both apps. Grant precise alarms and notification access to each. On Android 14+, also grant the ringing-screen permission if requested. Add an alarm and use Sync devices. Confirm the alarm appears on both before relying on it.

## Implemented source

- Dark native Android views with Alarms, Clock, Timer, Stopwatch and Bedtime tabs; compact watch layout. This uses platform views, not Compose.
- Alarm creation, editing, labels, weekdays, enable/disable, sound/vibration switches and configurable snooze. System alarm sound is used.
- Local `setAlarmClock` scheduling on both devices, reboot/time/timezone restoration, persisted records and deletion tombstones.
- Wear Data Layer alarm records; last-write-wins by revision then device ID. Commands update the alarm record and occurrence cutoff, allowing connected devices to stop ringing together.
- Alarm foreground service with sound, vibration, notification and full-screen intent; ten-minute automatic dismissal.
- Focused ringing screen: accumulate rotary movement to snooze or dismiss, with progress and haptics. Clockwise is mapped to snooze, counterclockwise to dismiss. **Direction and thresholds need verification on the actual Galaxy Watch 4 Classic.** Touch buttons remain available.
- World clocks with zone selection; multiple minute-based timers; persistent stopwatch with laps; sleep goal and a wake-up alarm schedule.
- Unit test source for recurrence, snooze, disabled alarms and daylight-saving transitions.

## Important limits and unfinished work

- Source has not been compiled or device-tested. Do not rely on this prototype for critical alarms.
- Full Google Clock parity is not implemented: custom sounds/music services, Assistant routines, bedtime reminders, sleep tracking, detailed timer controls, polished round-screen editing and accessibility tuning remain.
- Data Layer delivery is eventually consistent. Offline alarms ring independently; offline dismissal cannot immediately silence the other device. Concurrent edits use wall-clock revision ordering; no distributed clock correction or per-field merge is implemented.
- Timers are shared alarm records; stopwatch, world-clock choices and sleep goal are local. There is no live connection indicator or delivery acknowledgement UI.
- Alarm editing on the watch uses platform dialogs and needs usability work on the small round screen. Simultaneous alarms have basic handling and need further validation.
- OS policy controls full-screen presentation. If the full-screen screen is not shown, tap the alarm notification. The bezel is handled only while the app's screen has focus.
- Android force-stop prevents alarm delivery until the app is opened again. Notification channels and OEM power policies must be checked on the target phone/watch.

## Required device acceptance checks

- Compile both variants and run the unit tests before installing.
- Create/edit/delete from each device, reconnect after offline changes, and check convergence.
- Ring with screen locked, app process killed, Bluetooth disconnected and Doze active; test after reboot and timezone changes.
- Snooze/dismiss on each device and confirm both stop and schedule the next occurrence correctly.
- Check bezel direction, thresholds, accidental movement, touch fallback, screen focus, and notification/full-screen permissions on Watch 4 Classic.
- Test concurrent alarms/timers, timeout, DST boundaries, permission revocation and reinstall with matching signing keys.

## Run the available tests

Run `bash tools/test-core.sh` with Java 17+ installed. It compiles and executes the same Java timing and rotary-gesture classes called by the Kotlin app, without Android or external dependencies. If a `javac` executable is missing, the script uses the JDK compiler module via `java`. Saved results are in `verification/core-checks.txt`.

The existing Kotlin/JUnit tests still require Gradle and Android dependencies and have not run here.

## Automated debug APK build

`.github/workflows/android-debug.yml` installs the toolchain on a GitHub-hosted runner, runs the policy checks, builds both debug apps, runs Kotlin unit tests and Android lint, and uploads the APKs and reports. It runs on push, pull request or manual dispatch. This workflow has been prepared but **has not been executed or verified**. No repository has been created or published.

After adding this project to a GitHub repository, the Actions tab will expose the workflow. Both apps now explicitly use the checked-in development-only `debug.keystore`, so later builds can update earlier builds without changing signing identity. The password is the standard `android`; this key is public development material and must never sign a production release. Keep the same key for future debug updates. For local builds, use the commands above.

## Debugging status

- Fixed the timing policy to exclude snooze timestamps already covered by a dismissal cutoff.
- Rejected stale snooze/dismiss actions for disabled/deleted alarms or invalid occurrence IDs.
- Extracted bezel accumulation into the production `RotaryGesture` component; covered thresholds, direction reversal, timeout reset, invalid input and single-action completion.
- XML parsing passed for all three manifests; this is not Android manifest merger or lint validation.
- APK compilation, Android lint and Kotlin/JUnit execution remain blocked by missing Android/Gradle tooling and the unavailable network proxy.
- No app launch, on-device alarm delivery, Bluetooth synchronization or physical bezel tests have been performed.

## Phone-only iteration and downloads

The workflow now publishes two individual APK assets under the `debug-latest` prerelease after a successful default-branch build, in addition to the Actions artifacts. Once the repository is connected and the first build succeeds, the stable links will be:

- `https://github.com/OWNER/REPO/releases/download/debug-latest/orbit-clock-phone-debug.apk`
- `https://github.com/OWNER/REPO/releases/download/debug-latest/orbit-clock-watch-debug.apk`

Replace OWNER/REPO with the actual private repository. Private release downloads require GitHub authentication. The workflow has not yet run; these URLs are templates, not existing downloads.

For changes requested from a phone, the coding session must have repository write access and build-status access. A prompt in a session without those connections cannot edit the repository or trigger builds. With those connections, the process is: implement change, push to the repository, wait for CI, fix failures, and return the successful APK links. ChatGPT on the phone does not itself provide a compiler.

On Android, allow installation from the browser/download app when asked. A debug APK is an ordinary installable app with debugging enabled; it does not attach a debugger automatically. Installing a matching signed update normally preserves app data. Installing the watch APK still requires Wear OS installation tooling or wireless ADB; downloading it onto the phone does not install it on the watch.

The debug keystore is intentionally shared in this source project for repeatable development builds. Switch to a separate private signing key before any production distribution. CI derives version codes from the repository workflow run number. Keep using the same repository/workflow for increasing versions.
