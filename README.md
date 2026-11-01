# Orbit Clock

Native Android phone and Wear OS clock apps with a Material You / Material 3 interface, shared alarms, and rotating-bezel controls for Galaxy Watch 4 Classic.

Both debug APKs build successfully. Seven Kotlin/JUnit tests, 71 standalone production-policy checks and phone/watch lint pass. Targeted emulator checks exercise the ringing UI, occurrence snooze selection, volume buttons, real heads-up actions over another app and native motion. See [verification/STATUS.md](verification/STATUS.md) for exact revisions and outcomes. Physical-device testing remains. This is an initial prototype, not full Google Clock parity or a validated alarm replacement.

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

This app owns its alarms. It does not read or modify Google Clock's private alarm database. Music-service alarm sound integrations, Assistant routines, sleep tracking, bedtime reminders, watch hardware validation and further accessibility tuning remain unfinished. World-clock choices, stopwatch state and sleep goals are local to each device. Offline dismissal cannot immediately stop the other device, and synchronization is eventually consistent.

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

Pair the watch using Galaxy Wearable and open both apps. Grant notifications on both devices. On watches running Android 13 / Wear OS 4 or newer, the watch APK uses the dedicated-alarm permission granted during installation, so precise-alarm setup does not need the system Alarms and reminders page. Older watches and the phone app still use the precise-alarm approval flow. Install the watch update over the existing app to preserve saved alarms; reopen Orbit afterward to restore their schedules. On Android 14+, allow the ringing screen/full-screen intent if requested. Confirm an alarm appears on both devices before testing it. A debug APK is installable with debugging enabled; it does not automatically attach a live debugger.

## Watch precise-alarm setup — release 0.1.45

The watch APK now uses automatic dedicated-alarm permission on Android 13 / Wear OS 4 and newer, avoiding the Samsung Alarms and reminders screen reported to load indefinitely. Update over the existing watch app and reopen it; saved alarms are preserved and scheduling is restored. Older-watch and phone permission flows are unchanged.

Release **0.1.45** (version code **46**), source `c213a5afabdf1b03bc4c87f0c2316d9c44078160`: [build, unit/policy checks, phone/watch lint and APK publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41310315805) passed. [Wear OS 4 / API 33 verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41310315905) passed the runtime scenarios: fresh automatic exact-alarm access; the previous APK denied access; an in-place update preserving and rescheduling the saved alarm; and a UI-created one-minute timer delivered to the foreground ringing service with no Orbit crash. No exact-alarm appop grant is applied to the fixed APK. Both merged manifests are checked to keep the phone permission flow unchanged.

The emulator is a Wear OS image, not Samsung's physical device. The user's reported 6.0 may be One UI Watch; precise device OS mapping and physical-watch acceptance remain unverified. The test environment simulates unplugged/discharging battery state so Wear OS's charging activity cannot obscure Orbit. Ringing service detection accepts Android's abbreviated ComponentName.

## Request changes and builds from a phone

The intended flow is: request a change → a coding session with access updates this repository → GitHub Actions builds/tests it → download the successful APKs. Your Mac does not need to remain running for GitHub-hosted builds. The coding session must have repository write access and the ability to inspect Actions results; signing into ChatGPT alone does not provide those capabilities.

The [Android debug workflow](.github/workflows/android-debug.yml) runs on push, pull request or manual dispatch. After a successful default-branch build, it is configured to publish individual APKs to the `debug-latest` prerelease as well as Actions artifacts. **Verified:** [cloud build, tests, lint and publication succeeded](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41104668417). Both phone and watch debug APKs are built and uploaded from the verified revision; the development signing key is unchanged.

Latest debug APK links (updated after successful main-branch builds; see verification/STATUS.md for revision-specific UI evidence):

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

## Ringing screen

The phone follows the user-selected Google Clock reference: a black background with localized time-of-day corner lighting, large time, saved custom label (or “Alarm” when blank), and a Snooze/Stop rail with a draggable center thumb. A compact minus/minutes/plus row sits above it, with five-minute steps from 5 to 60. Both volume buttons still snooze while the ringing screen is focused. Accessible action labels also support tapping. Watch controls retain their compact dark layout.

## Ringing and notification UI

Only the ringing slider shimmers. Other screens retain static affordances and normal native press, value and navigation feedback, without shimmer or prose tips.

The clock uses weight 350, the 24sp label weight 400, duration weight 500, and 22sp rail labels weight 450. The main field stays black while an upper corner carries restrained light: blue/orange dawn, fresh yellow morning sunshine, blue-sky noon, gold/teal afternoon, coral/orange sunset, deep-blue late evening, or night with five tiny stars and a small crescent. Adjacent scenes crossfade at their corners without moving across the clock or controls.

The clock and background use actual current local time on opening/resume, clock/time-zone changes and visible minute boundaries. An alarm snoozed from 3 pm until 11 pm therefore re-rings with night artwork, preserving its saved time and default snooze duration. Shaders are cached per minute and viewport and clipped to the light regions; there is no background animation loop or new permission. See [verification/STATUS.md](verification/STATUS.md) for the successful scenario evidence and the latest full rerun's SystemUI setup blocker.

Phone alarm banners have Snooze/Stop controls, plus five-minute snooze adjustments in the heads-up/expanded layout. Collapsed notifications retain both actions. The banner and ringing screen share selection for that occurrence without editing the saved default. Android owns the notification header, dimensions and presentation; banner actions are taps rather than a draggable gesture. Wear notifications use native Snooze/Stop actions. See [interaction details](docs/INTERACTIONS.md).

## Watch ringing and bezel feedback

The watch uses its own compact ringing layout: larger circular +/- controls keep the selected snooze duration prominent, with smaller icon-only Snooze and Dismiss buttons below. Curved arrows show bezel direction: anticlockwise snoozes and clockwise dismisses, with the original rotation amounts. Turning the bezel expands the active control's layered aura outward while its touch target stays fixed. No instructional text or rotating percentage replaces the time/alarm label. The existing rotation amounts and snooze-duration selection are preserved. Phone ringing keeps its current slider.

Verified debug **0.1.61** (version code **62**), source `cc693c0cfa5c85462f23c08b9e3ff230ca53cccd`: [build, unit/policy checks, phone/watch lint and APK publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41316038225) passed. [Wear OS 4 / API 33 verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41316038217) passed all seven focused scenarios at both the default round-watch density and approximately 192dp width. Coverage includes injected native rotary events, anticlockwise Snooze and clockwise Dismiss, unchanged three-/six-unit thresholds, outward aura growth with fixed touch targets, pause/reversal resets, selected-duration preservation and 5–60 limits, 130% font scale, touch fallback and real AlarmManager/receiver delivery. No Orbit crashes were observed. Actual-display frames were reviewed internally. [Phone ringing visual-cue regression](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41316038243) also passed.

Physical Galaxy Watch 4 Classic detents, direction mapping, haptics and on-device reliability still require device confirmation. The development signing identity and alarm record schema are unchanged; update over the installed app to preserve records.


## Pause media when an alarm rings

Orbit sends Android's native Pause command and requests transient exclusive alarm audio focus when ringing begins. Successful focus requests are shared across overlapping alarms and released after the last alarm stops or snoozes. If Android 15 denies background audio focus, the native Pause command still operates and the alarm tone remains available. Orbit never sends Play; the media app controls whether it resumes after focus returns.

[All nine media emulator checks passed](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41320960051), including real music/video playback, overlapping alarms, Stop, Snooze and the Android 15 denial fallback. These checks used independent native test players. YouTube and physical-device audio have not been tested; apps must honor Android audio focus or media controls.
