# Orbit Clock

Native Android phone and Wear OS clock apps with a Material You / Material 3 interface, shared alarms, and rotating-bezel controls for Galaxy Watch 4 Classic.

Both debug APKs build successfully. Seven Kotlin/JUnit tests, 82 standalone production-policy checks and phone/watch lint pass. Targeted emulator checks exercise the ringing UI, occurrence snooze selection, volume buttons, real heads-up actions over another app and native motion. See [verification/STATUS.md](verification/STATUS.md) for exact revisions and outcomes. Physical-device testing remains. This is an initial prototype, not full Google Clock parity or a validated alarm replacement.

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

## Automatic and aligned snooze

Clock alarms auto-snooze after 30 seconds of ringing, using the currently selected duration. Manual and automatic snoozes advance from the delivered occurrence's minute-aligned time rather than adding the seconds spent ringing: 08:30 → 08:35 → 08:40 for five-minute snoozes. Passed slots are skipped to the next future slot, and duplicate delivery does not restart the timeout. Saved defaults remain unchanged. Timers retain their existing elapsed-time snooze and ten-minute stop timeout.

Builds, 82 policy checks and focused Android 15 clock-service verification pass. Watch runtime verification remains incomplete; see [verification status](verification/STATUS.md).

## Volume-button snooze

While the ringing screen is open and focused, either physical volume button snoozes on release using the selected duration. Held/repeated presses are consumed; other screens retain normal volume behavior. This shortcut does not intercept volume keys globally when only a notification is visible.

## Ringing screen

The phone follows the user-selected Google Clock reference: a black background with localized time-of-day corner lighting, large time, saved custom label (or “Alarm” when blank), and a Snooze/Stop rail with a draggable center thumb. A compact minus/minutes/plus row sits above it, with five-minute steps from 5 to 60. Both volume buttons still snooze while the ringing screen is focused. Accessible action labels also support tapping. Watch controls retain their compact dark layout.

## Ringing and notification UI

Only the phone ringing slider shimmers: two hollow semicircular light fronts expand from the thumb’s round ends and then stop. The pill stretches with Apple-referenced momentum and a damped rebound while its alarm icon stays on an independent, fixed-size layer. Touch and reduced motion cancel the cue. Other screens retain static affordances and normal native press, value and navigation feedback, without shimmer or prose tips. Actual emulator display captures confirm the icon retains its proportions through slow/fast dragging, holding and return; curved-front rendering, interruption, safe actions and reduced motion pass the focused native probe.

The pill stretches horizontally while its glyph stays crisp; it no longer tilts. Release velocity carries into a damped spring return that can be caught mid-motion. Destination labels fade before overlap. A deliberate physical drag commits an action; inertia or a tap catching an offset thumb cannot. See [verification status](verification/STATUS.md) for the passing focused native tests and separate broad-suite outcomes.

The clock uses weight 350, the 24sp label weight 400, duration weight 500, and 22sp rail labels weight 450. The main field stays black while an upper corner carries restrained light: blue/orange dawn, fresh yellow morning sunshine, blue-sky noon, gold/teal afternoon, coral/orange sunset, deep-blue late evening, or night with five tiny stars and a small crescent. Adjacent scenes crossfade at their corners without moving across the clock or controls.

The clock and background use actual current local time on opening/resume, clock/time-zone changes and visible minute boundaries. An alarm snoozed from 3 pm until 11 pm therefore re-rings with night artwork, preserving its saved time and default snooze duration. Shaders are cached per minute and viewport and clipped to the light regions; there is no background animation loop or new permission. See [verification/STATUS.md](verification/STATUS.md) for the successful scenario evidence and the latest full rerun's SystemUI setup blocker.

Phone alarm banners have Snooze/Stop controls, plus five-minute snooze adjustments in the heads-up/expanded layout. Collapsed notifications retain both actions. The banner and ringing screen share selection for that occurrence without editing the saved default. Android owns the notification header, dimensions and presentation; banner actions are taps rather than a draggable gesture. Wear notifications use native Snooze/Stop actions. See [interaction details](docs/INTERACTIONS.md).

## Watch ringing and bezel feedback

The watch keeps a large live time, the alarm label, a small snooze-duration value and large 56dp circular +/- targets. A short **68-degree bottom arc** stays clear of these buttons. **Clockwise snoozes on the left; anticlockwise dismisses on the right.** The action amounts remain three units for Snooze and six for Dismiss. Retargetable frame-paced motion retains velocity during reversals and partial returns. Completing an action stops the alarm immediately, then uses a soft expanding/fading transition to the watch face. Reduced motion skips the reveal. Phone controls are unchanged by this watch correction.

Verified debug **0.1.107** (version code **108**), source `cd63ef76673c9d2abf9185101ec4ffe7f3d3731e`. [Build, unit/policy checks, both APKs, lint and publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41477529149) passed. [Wear OS API33 native verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41477529167) passed all seven scenarios at both default and approximately 192dp round-watch sizes. Coverage includes clockwise three-unit Snooze, anticlockwise six-unit Dismiss, left/right motion, painted rail/thumb clearance of at least 4dp from the unchanged 56dp duration targets, bounded painted width, near-complete travel, intermediate draw frames, reversal/idle/touch interruption, 5–60 limits, 130% text, selected-duration preservation, real receiver delivery, reduced motion and the translucent return to the actual watch face. Actual rendered layout/near-end/large-text/transition frames were inspected internally at both sizes. Physical Galaxy Watch4 frame rate and bezel feel remain device acceptance checks.

The development signing identity and alarm record schema are unchanged; install over the existing app to preserve records.


## Pause media when an alarm rings

Orbit sends Android's native Pause command and requests transient exclusive alarm audio focus when ringing begins. Successful focus requests are shared across overlapping alarms and released after the last alarm stops or snoozes. If Android 15 denies background audio focus, the native Pause command still operates and the alarm tone remains available. Orbit never sends Play; the media app controls whether it resumes after focus returns.

[All nine media emulator checks passed](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41320960051), including real music/video playback, overlapping alarms, Stop, Snooze and the Android 15 denial fallback. These checks used independent native test players. YouTube and physical-device audio have not been tested; apps must honor Android audio focus or media controls.

## Dynamic home-screen widgets

The phone app includes **Orbit · Dynamic square** (about 2×2) and **Orbit · Dynamic wide** (about 4×1). Add them from your launcher's widget picker. Both are transparent clocks outside reminder windows. Square replaces the clock with one reminder. Wide keeps the clock on the left and uses one continuous full-width transparency gradient behind its native content, becoming darker toward the right for white reminder text. There is no separate right-hand card. Exact cell sizes depend on the launcher. Use wallpaper with adequate contrast for the white clock and its shadow.

Use the launcher's **Edit widget** action for shared alarm/bedtime windows of 10, 15, 30, 45, 60 or 90 minutes (default 15), a bedtime reminder switch, Add alarm and the bedtime schedule. Tap the clock to open Orbit, an alarm reminder to edit that alarm, or the moon reminder to open Bedtime. The alarm's On control disables the saved alarm, including its repeats; it is not a one-occurrence skip.

The display follows installed alarm occurrences and actual snooze deadlines, excludes timers/deleted/disabled alarms, and gives ringing/nearby alarms priority over bedtime. Bedtime uses the existing enabled **Wake up** alarm's local time, repeat days and saved sleep goal; snoozing never moves bedtime. “It's bedtime” expires after 15 minutes. The launcher renders the live clock/countdown. Non-wakeup boundary updates and periodic recovery avoid a background polling service; Android sleep/launcher restrictions may delay visual transitions without changing core ringing schedules. No Wear OS widget or Google Clock private-data integration is added.

The rebuilt surface is verified in debug **0.1.109**: native phone-emulator checks passed at normal and 130% text size, including light/dark/patterned wallpaper, minimum sizes and updating an existing widget without deleting it. Physical launcher behavior still needs device acceptance.

The wide widget uses a one-row layout, verified in debug **0.1.111** at normal and130% text sizes. New placements default to4×1; if an existing placement keeps its old height, use the launcher's resize handles to reduce it to one row.

## Pomodoro Focus mode

Timer → Focus mode offers phone/watch focus and break cycles, configurable durations, Pause/Resume and confirmed End. The focused watch screen supports clockwise Start/Resume and anticlockwise Pause. See [Focus behavior, timing and sync limits](docs/FOCUS.md). Existing widgets are unchanged. See HANDOFF.md for verification status before treating a build as ready.

Verified Pomodoro test APKs: [preview 0.1.119](https://github.com/vish9731-agentic/orbit-clock/releases/tag/pomodoro-preview-0.1.119). Phone and Wear OS Focus runtime checks pass; PR #4 awaits approval for integration into main.
