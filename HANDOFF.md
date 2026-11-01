# Orbit Clock handoff

## Goal and authorization

The user wants a personal Android phone + Galaxy Watch 4 Classic clock app inspired by Google Clock, with shared alarms and rotary-bezel snooze/dismiss. They want to request changes from their phone and receive downloadable debug APKs without keeping a Mac running.

The user authorized creating the private personal repository, uploading this project, fixing build failures, running tests and publishing debug APK downloads. Login and account authorization are completed by the user. Do not use Bytebeam repositories for this project.

## Watch bezel ringing controls — current change

The user requested a distinct watch ringing UI: larger circular plus/minus controls with the snooze duration intact, and small circular Snooze/Dismiss icon buttons that expand outward through an aura during bezel rotation. No visible bezel instructions, action words or percentage text. The time/alarm heading remains stable. Native 48dp action touch targets stay fixed while the visible cores grow from 32dp to 44dp and layered radial halos spread from both sides. Curved arrows indicate anticlockwise Snooze and clockwise Dismiss. Direct progress, reversal and idle reset are graphical, with no decorative animation loop or action tied to animation completion.

RotaryGesture is unchanged: negative three units snoozes, positive six dismisses, reversal/pause semantics remain, and saved/per-occurrence duration behavior is preserved. The watch duration buttons increase from 40dp to 56dp, with larger +/- glyphs. Side spacing and heading width adapt down to a 192dp round viewport; focused runtime checks cover both the default and minimum-width density. The phone retains its existing rail and duration layout. Build and Wear OS bezel scenarios are pending CI; physical bezel behavior still needs the user's watch.

## Watch exact-alarm permission

The user installed Orbit on a Galaxy Watch 4 Classic but Samsung Settings > Alarms and reminders spins indefinitely. They report software version 6.0 (possibly One UI Watch; exact Android API level was not captured). The watch manifest now uses USE_EXACT_ALARM for API 33+ and limits inherited SCHEDULE_EXACT_ALARM to API 32. Orbit is a dedicated alarm/timer app. This avoids manual precise-alarm approval on Android 13 / Wear OS 4 and newer while preserving the older-OS flow. The phone manifest and permission behavior are unchanged. Keep the shared signing key so an in-place watch APK update preserves records; reopening the app runs its existing schedule restoration.

Verification: Release **0.1.45** (version code **46**), source `c213a5afabdf1b03bc4c87f0c2316d9c44078160`: [build, unit/policy checks, phone/watch lint and APK publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41310315805) passed. [Wear OS 4 / API 33 verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41310315905) passed the runtime scenarios: fresh automatic exact-alarm access; the previous APK denied access; an in-place update preserving and rescheduling the saved alarm; and a UI-created one-minute timer delivered to the foreground ringing service with no Orbit crash. No exact-alarm appop grant is applied to the fixed APK. Both merged manifests are checked to keep the phone permission flow unchanged.

The emulator is a Wear OS image, not Samsung's physical device. The user's reported 6.0 may be One UI Watch; precise device OS mapping and physical-watch acceptance remain unverified. The test environment simulates unplugged/discharging battery state so Wear OS's charging activity cannot obscure Orbit. Ringing service detection accepts Android's abbreviated ComponentName.

This change avoids the affected Settings page; it does not repair Samsung Settings. The focused Wear OS permission workflow checks install/update and actual alarm delivery where the hosted image is available. Physical Watch 4 Classic behavior and the user's exact OS build still require device confirmation. Notification and full-screen permissions are separate.

## Ringing atmosphere and actionable banners — current change

The latest user preference supersedes the earlier visual-guidance rollout: shimmer belongs only on the ringing slider. Main-tool shimmer bindings are removed and VisualHints rejects non-ring keys. Keep ordinary native navigation/press feedback, static edit/remove icons and no prose tips.

Ringing type is explicit: weight 350 for the large clock, 400 for the 24sp label, 500 for duration, and 450 for 22sp Snooze/Stop labels. The phone background is a static near-black gradient with a tiny local-time tint. RingAtmosphere blends through deep-night blue, dawn rose, morning sage, afternoon amber, evening violet and night indigo. All RGB channels stay <=20/255, with continuous midnight wrapping. Watch native dark colors remain unchanged.

Phone notifications now have functional Snooze/Stop in collapsed, expanded and heads-up views. Expanded/heads-up controls also adjust the occurrence snooze in five-minute steps, 5–60. The banner is a compact visual relative of the screen, with tappable actions; Android does not expose a draggable activity-style slider in RemoteViews and owns its system header, size and presentation. Wear uses native notification actions. No overlay or other new runtime permission is requested.

RingControls shares the per-occurrence selection between RingActivity and notification controls without changing the saved alarm default. Immutable PendingIntent URIs include alarm ID, occurrence and action. A non-exported receiver validates Scheduler.isActive before each adjustment/action. Selection broadcasts update both UI surfaces; repeated notification updates use alert-once behavior.

Source `6803f4b4a3f6b5a1c9a04721c1f281e1082d2dbd`: [build/tests/lint/publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41306472775) passed for phone/watch. Seven Kotlin tests and 54 standalone production checks passed, including all-minute darkness/continuity and time-period colors. [Targeted emulator run](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41306472764) passed 24 ringing/notification scenarios before a harness navigation failure (it remained in Settings before the next Timer/motion check). These verified main-tool shimmer absence, normal/dark and large-type ringing, duration caps/recreation, spring cancellation, actual selected snooze scheduling, Stop, both volume buttons, and the real heads-up rail plus/15-minute Snooze/Stop over Settings. Internal ringing and popup captures were visually reviewed.

The harness correction explicitly returns to MainActivity before subsequent motion checks. Product code is unchanged by that correction. The focused [notification rerun](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41307922974), source `336ca5c9f0ff18c1669613846ffe0f3869e3f000`, again passed all four heads-up action scenarios and inline validation. Its unrelated transition check failed because the 3-second recording contained only Timer; the later failure screenshot showed Stopwatch. The recording window is extended to 12 seconds without lowering the intermediate-frame assertions. Product animation code is unchanged.

Verified UI source `26d2e397da90bd9ad68883342a13c9142313c2e2` (product unchanged from 6803f4b4) publishes **0.1.42, version code 43**. [Build, 7 Kotlin tests, 54 production checks, phone/watch APKs, lint and publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41309429638): passed. [Focused motion verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41309429643): **5 scenarios passed**, including direct intermediate-scene proof, destination settling, rapid navigation, disabled animations and an empty crash buffer. Its internal filmstrip was reviewed. This is combined evidence across explicit scopes, not a claim that the earlier broader run completed successfully.

The 12-second recording contains a real intermediate blend, but the older four-changing-frames count was sensitive to guest frame emission and incidental input-focus changes. The harness now masks pixels that actually differ between source and destination, requires an intermediate frame visibly distinct from both endpoints, verifies the paused Stopwatch destination and checks that it settles. It does not claim a physical frame-rate guarantee. No product animation code changed during these test corrections.

Physical phone/watch behavior and OEM notification differences remain device acceptance checks. Emulator feedback is internal; provide APKs rather than routine report/screenshot downloads.

## Previous visual-only guidance — 0.1.36

The user explicitly rejected explanatory hint text. Do not restore coach-tip rows or ringing captions. Keep normal action labels, validation/permission messages and screen-reader descriptions. The ringing rail shows two outward light sweeps; key controls receive finite highlights, alarm times have a pencil affordance on phone, and city cards have an explicit remove icon with confirmation. Narrow watch city cards stack their contents with the remove action below. No decorative loop or added hint panel is used.

VisualHints uses view overlays with no touch interception, layout changes, alarm actions or timing dependencies. Cues require focused visible controls, stop on interaction/detach and honor disabled animations. They show once per app process across at most three eligible sessions, with learning suppressing relevant cues earlier. The visual preference namespace is separate from the retired prose hints.

Build delivery: release 0.1.36, version code 37; source `ecd61e80b235cb8a43497f149bb78022e1b88a08` (app code unchanged from `b0e27d462de0cc50564c26dbcf53b4f19d4ded94`), [build/tests/lint/publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41303204522) passed for both APKs. Seven Kotlin/JUnit tests and 44 production-policy checks pass. Development signing identity is unchanged.

[Full phone emulator run](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41301031850): **48 scenarios passed**, empty crash buffer, on source `149078d3e9d90fc4655ef237b1eab94c682fc707`. The later app changes only adapt narrow-watch geometry. [Focused fresh-install verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41302598698): **7 scenarios passed** on source `fea643d6811a34d306621e5ab576080ea3bc8d06`, including no tip rows across all tools, city removal confirmation/cancellation, preset learning, truthful connection feedback, native back draft behavior and no crashes.

[Focused visual-cue run](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41303204615): **3 scenarios passed** on final release source `ecd61e80b235cb8a43497f149bb78022e1b88a08`. Internal frames from the real ringing/notification path were inspected: the rail emits outward light bands, remains readable with its thumb centered, and settles without an ongoing shimmer. Stop remains functional and the crash buffer is empty. The 40-second capture includes notification opening; the initial shorter recording missed the cue and was replaced. Main-screen and large-type ringing captures were also reviewed.

The harness now paces injected text, verifies the actual field before saving, and checks stable control bounds before taps around IME/sheet transitions. Empty alarm preferences on a fresh install are treated as an empty store. These fix observed harness failures without automatically retrying user actions.

Emulator images/video are development feedback only. Do not routinely send downloadable test reports or screenshots. Physical phone/watch key behavior, Wear OS rendering, cross-device delivery, haptics and bezel hardware still need target-device testing. This remains an app that owns its own alarms, without private Google Clock access or full parity.

## Previous verified interaction update — 0.1.30

Source `69380559ed71382174005744801fbcca14e91d4a`, version code 31. [Build/tests/lint/publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41291899614) and [full Android 15 emulator suite](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41291899547) passed. Both release APK assets are uploaded; the shared signing identity is unchanged. Seven Kotlin/JUnit tests and 44 production-policy checks pass.

All **48 emulator scenarios** passed with an empty crash buffer. Coverage includes all five tools, saved alarm persistence, large type, real timer delivery, five-to-sixty-minute snooze limits, recreation, actual selected snooze deadlines, separate Stop, held volume up, volume down with animations disabled, interruptible spring cancellation, hint learning/dismissal/session caps, city removal, bounded truthful connection feedback, canceled native back/draft preservation, committed back without saving, inline-error recovery, intermediate motion frames, rapid tab changes and reduced-motion operation. Internal screenshots and transition video frames were reviewed; they are feedback for development, not routine user deliverables.

The approved dark green ringing reference remains: light large time, custom label or Alarm, compact duration row and Snooze/Stop rail. Action labels are 24sp regular. Navigation chrome stays mounted around native Material shared-axis transitions with overlapping fades. The slider uses raw finger velocity and an interruptible bounded native spring; actions execute immediately on release, never in animation completion callbacks. Native FAB/component motion and system haptic preferences are preserved.

Contextual hints retire after demonstrated use or dismissal and otherwise cap at three visible app-process sessions. Rotations and rerenders do not consume extra impressions. Ringing guidance progresses from swipe directions to volume shortcut; watch education explains bezel control without enlarging the ringing layout. Sync feedback reports observed connectivity/request status or timeout; it never claims unacknowledged delivery. Both manifests opt into native predictive back with older-platform fallback. See [reference mapping](docs/INTERACTIONS.md) and [motion inventory](docs/MOTION.md).

Phone emulator evidence cannot establish physical frame rate, haptic quality, Wear OS layout/usability, Bluetooth delivery, hardware-key behavior on the user's phone, or physical bezel direction/thresholds. Those device checks and full Google Clock feature parity remain outstanding. The app owns its alarms; Google Clock private alarms are not imported.

## Current state

- Private repository exists: https://github.com/vish9731-agentic/orbit-clock. Personal-account access and admin/push permissions were confirmed.
- Source lives at `/workspace/orbit-clock`. Modules: `core`, `phone`, `wear`. JDK 17, SDK 35, AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9; Gradle wrapper is included.
- Local build succeeded for `:core:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug`.
- Current verification: see the current-change section above for exact source/run coverage. The latest build passes 7 Kotlin/JUnit tests, 54 production-policy checks, both APK builds and lint.
- Both debug APKs exist under their modules' `build/outputs/apk/debug/` directories. APK v2 signature verification passed for both with matching certificate SHA-256 `d3c6b53474417ca21c9e094ffa4a1eefd021af614f6267a2c882b993397e4f4a`.
- Android 15 phone-emulator startup smoke test passed: https://github.com/vish9731-agentic/orbit-clock/actions/runs/41171287946. APK installation and launch succeeded, the app process remained alive after 10 seconds, the crash buffer was empty, and a screenshot was captured. Hardware, Bluetooth delivery and physical-bezel tests remain.
- Repository upload is complete. Cloud build/tests/lint/publication passed: https://github.com/vish9731-agentic/orbit-clock/actions/runs/41104668417. Release: https://github.com/vish9731-agentic/orbit-clock/releases/tag/debug-latest. Downloaded both cloud artifacts and release APKs, confirmed byte-for-byte matches, matching debug certificates, version code 3 and debuggable manifest. The first attempt failed on retired SDK package `tools`; the workflow now installs explicit supported packages.

## Implementation and limits

Native Kotlin platform-view prototype: alarm CRUD, independent local scheduling, Wear Data Layer records, ringing service, touch/rotary controls, world clocks, timers, stopwatch and basic bedtime UI. It owns its alarms and does not access Google Clock's private alarm database. Full Google Clock parity and polished watch editing are unfinished. Synchronization is eventually consistent; disconnected devices cannot immediately silence each other.

Both variants deliberately use application ID `dev.orbit.clock` and the same checked-in development-only `debug.keystore`. Never replace the key casually or use it for production. Keep CI version codes increasing. A downloaded debug APK enables debugging but does not automatically attach a debugger. Watch APK installation is a separate Wear OS/ADB step.

## Continue

1. Continue user-requested changes in this repository, test, push and inspect the new Actions run. Existing default-branch build and APK delivery have been verified.
2. Verify each future build before returning its links. Existing release assets: `orbit-clock-phone-debug.apk` and `orbit-clock-watch-debug.apk`.
3. Keep README and verification status current after material changes.
4. Verify future phone-accessible coding sessions can edit/push this repository, inspect CI and return APK links. Shared chat history alone is not repository write access.
5. Install on the user's phone/watch and test locked-screen ringing, Doze, reboot, concurrent alarms, offline/reconnect sync, snooze/dismiss and physical bezel behavior.

Read actual tool availability and environment permissions before acting. Previous connection blockers are historical; do not repeat obsolete instructions to reconnect or create a repository that now exists. Keep network/build attempts bounded and give timely progress updates. Never ask for credentials in chat or claim hardware validation without devices.

## Cloud emulator verification

[Successful Android 15 emulator run](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41171287946) on source commit a3045ab4e29de9bb4cfae83c494db04b0f4cf925. Installation and launch succeeded; the process remained alive after the 10-second observation, with an empty crash log. [Screenshot and launch evidence](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41171287946/artifacts/13030569550) contains screenshot.png, launch.txt, pid.txt and crashes.txt. This checks startup only, not alarm reliability, watch sync or bezel hardware.

The previous emulator run failed before boot because the hosted runner lacked disk space. The workflow now removes unused SDKs from the disposable runner before boot and limits boot waiting to six minutes.

When shell networking is restricted, use the GitHub connector: fetch https://api.github.com/repos/vish9731-agentic/orbit-clock/actions/runs for run discovery, then fetch_workflow_run_jobs, fetch_workflow_job_logs and fetch_workflow_run_artifacts. Workflow-specific discovery URLs may be rejected; the actions/runs collection works. Do not repeat stalled shell-network approval requests.

## Phone prompting and emulator feedback

The user will request changes from their phone. Implement the change, then use a cloud Android emulator to exercise and verify the requested behavior before reporting completion. A successful build or launch-only smoke check is insufficient evidence for a feature change: add or adapt scenario-specific interactions and assertions, inspect the resulting UI/logs, fix defects and rerun relevant checks.

Use screenshots, logs and test artifacts as internal feedback when useful. Do not routinely send the user downloadable screenshots, test reports or artifact links; summarize what was verified and any remaining limitation. Provide those materials when requested. Existing APK delivery remains available.

Use a Wear OS emulator for watch-specific runtime behavior where feasible. A phone emulator cannot establish watch sync, physical bezel behavior or real-device alarm reliability. State any untested requirement explicitly rather than claiming all requested behavior works.

## Material You redesign — current verified revision

Source ce6db7d172217b6c0f9faf7aae2624eb36a1ed70; release version 0.1.12, version code 13. [Build/test/lint/publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41176289239) and [17 emulator UI scenarios](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41176288860) passed. Material Components 1.12.0, AppCompat API dependency 1.7.0 and Core KTX 1.13.1 back the shared native UI. Themes are Material3 DayNight; supported Android12+ phones use DynamicColors. Watch activity forces a dark theme and compact number pickers/weekdays. Physical Wear OS runtime remains unverified.

ClockActivity contains the redesigned five tabs and Material editor; OrbitDesign.kt provides shared color roles and scalable clock artwork. RingActivity uses a dark Material layout while retaining touch and rotary actions. No alarm schema or signing identity changed.

Emulator scenarios exercise empty state, time picker, labeled weekday alarm creation/toggle/restart persistence, city search/add, timer start/cancel, stopwatch start/lap/pause/reset, bedtime goal/wake-up schedule, dark mode and large-text editor/header/navigation, plus no-crash checks. Internal visual captures were inspected and used to fix navigation overlap, status-bar contrast, stopwatch text contrast and keyboard panning. Keep adjustResize in the activity manifests and avoid double-applying system-bar insets to the Material navigation bar. CI disables the Google SDK setup package on the disposable emulator to prevent unrelated setup ANR dialogs from blocking tests.

The user wants emulator feedback for verification, not routine downloadable screenshots or test-report links. The test script emits internal thumbnail feedback in CI logs and stores short-lived artifacts for diagnosis. Summarize results and provide APKs; share visual evidence only when requested.

## Current ringing snooze adjustment

Verified source 39e100f92e06b05c8806c66be94dcdf7533d6594; release 0.1.14, version code 15. [Build/test/lint/publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41196995849) and [24 emulator scenarios](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41196995861) passed. The ringing UI adds only a centered minus/minutes/plus row above Snooze. Five-minute steps, minimum 5, maximum 60, disabled buttons at limits. Existing custom defaults within range are retained rather than snapped to multiples of five.

RingActivity stores the selected duration in instance state for the same alarm ID and occurrence, resets it for another occurrence, and passes it to Scheduler.act only for snooze. SnoozeDuration.java centralizes limits; Scheduler's optional override leaves the saved alarm default unchanged. Both touch and bezel snooze call the shared handler. Adjustment alone does not write/reschedule/sync the alarm; the actual snooze action writes the chosen future deadline through the existing sync path. Watch header/spacing are compact to keep the extra controls visible; physical watch runtime remains unverified.

New emulator tests use real one-minute timers via the app UI, AlarmManager and notification. They verify 5/60 limits and disabled state, no scheduling/default mutation while editing, selection after font-scale recreation, actual 25-minute snooze record and scheduled deadline, and dismissal of another ringing timer without altering the first snooze. Debug run-as is used only by CI to read fixture preferences; no exported test entry point was added. Internal ringing captures were visually inspected. Continue to avoid routine screenshot/report links in user responses.

## Volume-button snooze — release 0.1.17

Source 3e5f55a7e71c608a1b6de5ebb87787b8d9c4078a, version code 18. Both volume up and volume down snooze on release while RingActivity is open and focused, through the same selected-duration handler as touch and bezel. The whole press and repeats are consumed; canceled/unmatched releases do not act, and focus loss/pause resets the tracked press. Other screens retain normal volume handling. Notification-only/background interception is not implemented.

[Build, unit tests, 44 policy checks, both APKs, lint and publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41263319591) passed. [Android 15 emulator scenarios](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41263319565): **26 scenarios passed**, with no app crashes. The new scenarios use real timer deliveries and assert that held volume up schedules the selected 15 minutes, volume down schedules the selected 5 minutes, saved defaults remain unchanged and the corresponding AlarmManager schedule matches. Physical-device key behavior and watch runtime still require device acceptance tests.



## Material You ringing redesign — release 0.1.18

Source ad12f1416e2303865d7238d1e5e814a7d8149257, version code 19. RingActivity now uses the phone Material3 DayNight theme with DynamicColors, a scalable scalloped clock face, separate AM/PM, alarm/timer badge and label, a rounded bottom snooze card with minus/minutes/plus, a prominent icon Snooze button and quiet Dismiss action. System-bar contrast follows the resolved theme. Watch retains its compact dark layout. Selection persistence and touch/volume/rotary handlers are retained.

[Build, unit tests, 44 policy checks, phone/watch APKs, lint and publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41274959680) passed. [Android 15 emulator](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41274959672) passed all **28 scenarios** with no crashes, including new light/dark surface and 130% font-scale action visibility checks, 5–60-minute limits, selection recreation, actual snooze deadlines, touch Dismiss and both volume-button snoozes. Light, dark and large-type ringing captures were visually inspected internally. No physical phone or watch runtime test was performed.

## Selected Google Clock ringing reference — release 0.1.20

Source 68722e72a385c1a077b366631a3f154923ab8385, version code 21. The user rejected the custom scalloped clock/card redesign and explicitly selected a screenshot with a plain dark green background, large digital time, label beneath and horizontal Snooze/Stop slider. Preserve this reference direction; do not reintroduce decorative clock faces or cards without a new request.

RingActivity renders the trimmed saved label, falling back to "Alarm" when blank. The editor already saves this default for blank labels. A compact five-minute minus/minutes/plus row (5–60) sits just above AlarmSlideControl. Its center thumb drags left to snooze or right to stop; short/canceled drags reset, and accessible action buttons support tapping. Volume and rotary handlers retain selected-duration behavior. Phone status bar hides while navigation stays available, avoiding Android’s first-use immersive tutorial that blocked the first emulator attempt. The unused RingingFace artwork was removed. Watch retains compact controls, now labeled Snooze/Stop.

[Build, unit tests, 44 policy checks, both APKs, lint and publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41280770592) passed. [Android 15 emulator](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41280770576) passed **29 scenarios** without crashes: real ringing with saved-label rendering, stable dark green surface in both system themes, action visibility at 130% font scale, 5/60 limits, selection recreation, short-drag cancellation, actual swipe-left 25-minute scheduling, swipe-right Stop without altering an earlier snooze, held volume up and volume down. Normal and large-type ringing captures were visually compared internally to the selected reference. Physical phone/watch behavior remains untested.
