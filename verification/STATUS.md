# Verification status

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

Contextual hints retire after demonstrated use or dismissal and otherwise cap at three visible app-process sessions. Rotations and rerenders do not consume extra impressions. Ringing guidance progresses from swipe directions to volume shortcut; watch education explains bezel control without enlarging the ringing layout. Sync feedback reports observed connectivity/request status or timeout; it never claims unacknowledged delivery. Both manifests opt into native predictive back with older-platform fallback. See [reference mapping](../docs/INTERACTIONS.md) and [motion inventory](../docs/MOTION.md).

Phone emulator evidence cannot establish physical frame rate, haptic quality, Wear OS layout/usability, Bluetooth delivery, hardware-key behavior on the user's phone, or physical bezel direction/thresholds. Those device checks and full Google Clock feature parity remain outstanding. The app owns its alarms; Google Clock private alarms are not imported.

## Historical evidence

## Completed locally

| Check | Result |
| --- | --- |
| `:phone:assembleDebug` | Passed; `phone/build/outputs/apk/debug/phone-debug.apk` produced |
| `:wear:assembleDebug` | Passed; `wear/build/outputs/apk/debug/wear-debug.apk` produced |
| `:core:testDebugUnitTest` | 7 tests, 0 failures, 0 errors, 0 skipped |
| `bash tools/test-core.sh` | 44 production timing/timer/rotary/snooze-policy checks passed |
| `:phone:lintDebug` | Passed; no errors |
| `:wear:lintDebug` | Passed; no errors |
| APK signatures | Both verified with APK Signature Scheme v2; matching signing certificate |

Signing-certificate SHA-256:

```text
d3c6b53474417ca21c9e094ffa4a1eefd021af614f6267a2c882b993397e4f4a
```

Evidence:

- Standalone check output: [core-checks.txt](core-checks.txt).
- Kotlin/JUnit XML: `core/build/test-results/testDebugUnitTest/TEST-dev.orbit.clock.core.AlarmTest.xml`.
- Unit-test HTML: `core/build/reports/tests/testDebugUnitTest/index.html`.
- Lint: each app module's `build/reports/lint-results-debug.html`, `.txt` and `.xml`.

The build command was `:core:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug`. These are real local Android builds and test results, not only XML parsing or planned checks.

## Cloud delivery

Private repository `vish9731-agentic/orbit-clock` exists; personal push access was exercised successfully.

- [Cloud workflow](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41104668417): **success**, including build, tests, lint, APK/report artifact upload and publish job.
- [Debug release](https://github.com/vish9731-agentic/orbit-clock/releases/tag/debug-latest): both APK assets present and downloaded successfully.
- Both release APKs match the downloaded cloud artifacts byte for byte and have the same signing certificate as the local build. The cloud phone APK manifest is debuggable, version `0.1.2`, version code `3`.
- The first cloud attempt failed because Android setup requested the retired `tools` package. Fixed by specifying `platform-tools`, `platforms;android-35` and `build-tools;34.0.0`; the corrected workflow passed.
- Repository editing, push, CI status/log inspection, artifact retrieval and authenticated release downloads have all been exercised from this cloud session. Browser control and on-device installation have not.

## Not yet tested

The phone app has now passed a real Android 15 emulator installation/startup smoke test. Physical-device and instrumentation tests have not run. Bluetooth/Wear Data Layer delivery, alarm service behavior under Android power/permission policies, full-screen presentation, watch UI usability, physical rotary-bezel direction/thresholds and signed updates remain device acceptance checks. Passing build, lint and policy tests does not establish alarm reliability or complete Google Clock feature parity.

## Cloud emulator verification

[Successful Android 15 emulator run](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41171287946) on source commit a3045ab4e29de9bb4cfae83c494db04b0f4cf925. Installation and launch succeeded; the process remained alive after the 10-second observation, with an empty crash log. [Screenshot and launch evidence](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41171287946/artifacts/13030569550) contains screenshot.png, launch.txt, pid.txt and crashes.txt. This checks startup only, not alarm reliability, watch sync or bezel hardware.

The previous emulator run failed before boot because the hosted runner lacked disk space. The workflow now removes unused SDKs from the disposable runner before boot and limits boot waiting to six minutes.

When shell networking is restricted, use the GitHub connector: fetch https://api.github.com/repos/vish9731-agentic/orbit-clock/actions/runs for run discovery, then fetch_workflow_run_jobs, fetch_workflow_job_logs and fetch_workflow_run_artifacts. Workflow-specific discovery URLs may be rejected; the actions/runs collection works. Do not repeat stalled shell-network approval requests.

## Material UI verification

- [Build, 7 Kotlin tests, 34 production-policy checks, both APKs and lint](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41176289239): **passed** on ce6db7d172217b6c0f9faf7aae2624eb36a1ed70.
- [Android 15 / Pixel 6 emulator interaction suite](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41176288860): **17 scenario checks passed**, including Material editor, alarm persistence/toggle, city search, timer create/cancel, stopwatch controls, bedtime settings, dark mode, 130% font size and persistent header/navigation. No app crash recorded.
- Light/dark layouts across all five phone tabs were visually inspected. Fixed issues found during review: navigation icon/label overlap, status-bar contrast, stopwatch contrast, and keyboard/large-text window panning. Test transitions now use bounded waits.
- Release 0.1.12 (version code 13) contains both uploaded APKs for the verified source. Development signing key and application ID are unchanged.
- Watch layout compiled successfully but was not run on a Wear OS emulator or physical watch. Actual alarm delivery, cross-device sync and bezel hardware still need target-device tests.

## Ringing snooze duration — release 0.1.14

- Source: 39e100f92e06b05c8806c66be94dcdf7533d6594; version code 15.
- [Build, 7 Kotlin tests, 44 standalone policy checks, phone/watch APKs, lint and publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41196995849): **passed**.
- [Android 15 emulator suite](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41196995861): **24 scenario checks passed** with no app crashes.
- Real timer delivery exercised the ringing screen. Verified minus to 5 and disabled lower control; five-minute additions through 60 and disabled upper control; selection unchanged after activity recreation; no model/schedule edits while adjusting; actual selected 25-minute snooze deadline persisted and scheduled; saved default unchanged; separate Dismiss did not snooze or disturb the earlier snoozed timer.
- Minimal ringing controls were visually inspected internally. Both release assets are uploaded. Watch APK compiles, but physical bezel and cross-device operation have not been hardware-tested.

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
