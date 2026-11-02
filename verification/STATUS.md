# Verification status

## Apple-referenced pill stretch and expanding semicircular shimmer — verified debug 98

The approved correction replaces straight light bands with two hollow, curved fronts expanding from the resting thumb’s semicircular endcaps. Cached radial gradients are clipped outward and to the rail. The phone receives two finite passes after 450ms over 1.8s, canceled by touch, lost focus, detach or reduced motion. No other-screen shimmer or hint prose is added.

The thumb is now an unscaled FrameLayout containing an independent fixed-size alarm ImageView above a geometry-drawn PillSurface. Only the surface elongates; its endcaps stay circular and the icon never inherits scale. Velocity and edge resistance produce bounded strain, held velocity strain relaxes after 110ms, and damped springs retain release momentum and allow catching/reversing at the visible position. The expanded visual capsule also receives touch beyond its fixed frame, without falling through to the action labels. The 70% physical movement/slop guard, exactly-once actions, volume snooze, selected duration and scheduling semantics are preserved. The separate watch WatchArcControl is unchanged by this phone correction.

References: [Apple’s slider momentum and deformation demo (18:07)](https://developer.apple.com/videos/play/wwdc2025/284/?time=1087) and [Designing Fluid Interfaces rubber-banding (17:01)](https://developer.apple.com/videos/play/wwdc2018/803/?time=1021). Motion is recreated with Android springs from the approved demos; no claim of Apple’s private implementation or exact proprietary curve.

Production source `189d95141f687586d8c6e0cc4c768810cf1d3c33`: [build, unit/policy tests, both lints and both APK publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41465737149) passed, publishing debug 98. [Focused native Android 15 run 41468479189](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41468479189) passed on test candidate `274244f38bbc95ebc1c916005f1249414a86a192`, with identical production slider/shimmer source. It verifies curved expanding/hollow/finite fronts, immediate cancellation, geometry-only stretch, actual hardware-display glyph invariance at rest/slow/fast/hold/return, release inertia, atomic catch/reverse, catch-tap safety, exactly-once left/right actions, focus/detach, real ViewGroup dispatch on stretched edges, compact-helper motion and reduced motion. The displayed glyph is 34×34px in all five samples. A partial release at 133.98px coasted to 169.79px without acting. Native hardware display frames and curved-rail composites were downloaded and visually reviewed internally.

The previous software-Canvas probe missed hardware RenderNode reuse of a counter-scaled onDraw icon; the new hierarchy removes that dependency. The new screenshot probe isolates the visible glyph from antialiased label fringes, retaining strict 2px dimension and 6% aspect tolerances. The first candidate failed only because those label specks contaminated the bounds; correcting the measurement required no production appearance change.

This same-key probe mounts the production component in a native themed activity. It establishes rendered interaction behavior, not physical-device frame rate or complete end-to-end alarm delivery. The compact helper check is not evidence for the separate WatchArcControl or physical bezel. Broad phone regression 41468479428 was still running when recorded; do not report it as passed. Later builds may include concurrent widget/watch work; the debug 98 release above is the verified production snapshot for this correction. The earlier debug 90 linear glints and counter-scaling description below is historical and superseded.

## Historical incoming-call-inspired glints and rubber-band slider — debug 90 (superseded)

The user requested Google Phone incoming-call-style shimmer, horizontal thumb stretch instead of tumble, and proper inertia. Reference: [Google's horizontal incoming-call announcement](https://support.google.com/phoneapp/thread/351208139/a-new-material-3-expressive-experience-in-the-phone-by-google-app?hl=en-GB) and [Android native spring guidance](https://developer.android.com/develop/ui/views/animations/spring-animation). Google's exact proprietary shimmer timing is not publicly documented; this is an adaptation, not a frame-exact reproduction.

Each new visible phone ringing rail receives two soft outward glints after 450ms, over 1.8s, then becomes quiet. Old learning/session-cap preferences do not suppress this requested incoming-alarm cue. Touch, focus loss, detach and reduced motion cancel it. Other screens receive no shimmer or hint prose. Cached gradient/path/matrix objects avoid rebuilding shaders on every frame.

The thumb stretches horizontally with a gently thinning profile; its alarm glyph is counter-scaled to stay undistorted, with zero rotation. Touch tracks directly, edge pull increases bounded tension, and native springs carry measured release velocity into a damped return. A new drag catches the current visible position. Destination labels fade before the stretching pill would cover their letters. Compact bezel progress retains continuous retargeting and existing physical three-/six-unit actions. Spring motion never dispatches alarm actions. Touch completion retains the 70% threshold and now additionally requires native touch slop and movement toward the action: catching/tapping or pulling back an inertially displaced thumb cannot snooze/dismiss. Accessible tap actions, selected duration, 30-second auto-snooze and aligned scheduling remain unchanged.

Source `9ff780911617e0b9430d5c61cd30d506354d1402`: [build, unit/policy tests, phone/watch lint and publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41437295788) passed; 82 policy checks pass. [Focused Android 15 native component verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41437295783) passed all eight behaviour groups: finite outward glints and touch cancellation, stretch/glyph proportions/label clearance, measured release inertia, catch/reverse continuity, catch-tap safety, exactly-once left/right actions plus cancel/focus/detach, compact bezel motion, and reduced motion. A partial release at 133.98px visually coasted to 186.67px without committing an action. Internal native View-rendered rest/stretch/return/glint/compact frames were downloaded and reviewed; the composited rail confirms subtle glints and clean label occlusion.

The separate same-key instrumentation mounts the actual production slider in a native Material-themed activity and dispatches component touch events. It checks Android view/spring behaviour and rendered pixels without changing alarm records; it never ships in Orbit. This is component verification, not an end-to-end ringing-alarm run or a physical frame-rate benchmark. An earlier continuity assertion compared different animation frames; it was replaced with the same tolerance measured atomically at touch-down.

[Wear regression 41436057507](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41436057507) passed at both watch densities on the same rendered motion before the catch-tap guard. In [latest Wear regression 41437295781](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41437295781), default density passed, while the approximately 192dp guest emitted only one sampled intermediate frame and failed its two-frame requirement. [Broad phone regression 41437295795](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41437295795) was blocked by a Pixel Launcher 'isn't responding' dialog, confirmed in its failure PNG; its initial MainActivity empty-state lookup ('No alarms yet') failed before ringing interactions. These are separate outcomes: do not claim the latest broad suites all passed. Physical phone/watch frame quality, haptics and bezel feel remain device acceptance checks.


## Thirty-second auto-snooze and drift-free clock deadlines — debug 84

Verified debug build 84, source `5aa2f30fb8c67bb8e6f2e130f82f2c35a65e71c3`: [build/tests/lint/publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41386614990) passed, with 82 policy checks and Kotlin/JUnit tests. [Focused Android 15 instrumentation](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41386615119) passed: real clock-alarm receiver deliveries, automatic snoozes measured at 30.593s and 30.067s, selected 5/15-minute overrides, unchanged saved defaults, exact minute-aligned deadlines, manual snooze and a duplicate start after 15s that did not extend the timeout. Eight consecutive five-minute advances and late/exact-boundary handling are covered by the production-policy checks.

The independent same-key probe calls production Store/Scheduler/RingControls and runs the real ringing service, without changing the emulator clock or navigating cold SystemUI. Its silent clock fixtures start with a short existing snooze deadline; it verifies the next installed deadline, not an uninterrupted multi-hour wait. The probe is never bundled in the published APKs. Earlier UI-driven attempts failed at package installation/SystemUI startup and were superseded by this passing probe.

[Phone regression](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41381605781) and [media regression](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41381605583) passed on the same production changes. [Wear regression](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41381605763) failed before ringing when its real timer fixture was not accepted; this is not a Watch runtime pass. Physical phone/watch timing, audio and cross-device delivery still need device acceptance.

## Localized current-time ringing atmosphere — debug build 81

The phone keeps a black main field with restrained light in an upper corner: blue/orange dawn, fresh yellow morning sunshine, blue-sky noon, gold/teal afternoon, coral/orange sunset, deep-blue late evening, and night with five tiny stars and a small crescent. The 08:00 sunshine scene is an additional seventh period. Neighboring scenes crossfade at their corners without moving across the clock or actions. The existing type, duration stepper, notification actions, ringing-only shimmer and media-pause behavior are preserved.

The visible phone clock and background use actual current local time on creation/resume, each visible minute boundary, and clock/time-zone broadcasts. The saved alarm hour never selects the scene. Re-ringing after a 15:00 alarm has been snoozed until 23:00 therefore uses night. The native drawable caches radial shaders per minute and viewport and clips drawing to the light regions. No decorative animation loop, location lookup, alarm schema, permission or signing change was added. Watch palette and concurrent watch-control changes are preserved.

[Build 41330182514](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41330182514), source `c852d7d7bd2063541585ed4fdbc37e1897a5bd7a`: **passed**, including 71 production-policy checks, Kotlin/JUnit tests, both debug APKs, phone/watch lint and publication. The policy checks cover all seven periods, night decoration, black main field, corner placement, continuous blends/midnight wrapping, local-zone selection and an eight-hour afternoon-to-night progression. The release assets retain the shared development signing identity.

Runtime evidence is combined across explicit scopes on unchanged atmosphere policy/drawing code: [41325830666](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41325830666) and [41327586229](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41327586229) verified seven distinct actual PNGs, visible clock/label/stepper/action pixels, localized colour with a black main field, and same-window 15→23 refresh. Actual hourly snooze deliveries through 20:00 passed in 41325830666; [41328771016](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41328771016) delivered all four 20→23 re-rings with unchanged saved alarm hour/default duration and an empty captured crash buffer. Its final full-background comparison failed because the temporary heads-up card covered the moon; independent comparison confirmed that the unoccluded background matched night, not afternoon. Original period PNGs were visually reviewed internally.

The latest [full atmosphere rerun 41330182485](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41330182485) is **failed/blocked during setup**, with SystemUI ANR dialogs before Add alarm in both guests; it must not be reported as a passing workflow. Previous guests also exited during longer stress runs. Capture now waits for natural banner clearance before comparing artwork, but this final clearance path has not completed a runtime run because setup failed first. Startup waits are bounded; only an explicitly identified SystemUI/Pixel Launcher dialog may receive one Wait action, while an Orbit ANR remains a failure.

These tests use only rooted disposable API 33 guests, simulated clock changes through that image's framework interface, and sound/vibration disabled in the visual fixture. Production uses ordinary public time APIs. Coverage of 15→19 and 19→23 is split between guests/runs; it is not an uninterrupted physical eight-hour reliability test. Physical rendering, long real-time snoozes, OEM notifications, Bluetooth sync and hardware controls still require target-device acceptance.


## Watch circular controls and bezel aura — verified 0.1.61

Verified debug **0.1.61** (version code **62**), source `cc693c0cfa5c85462f23c08b9e3ff230ca53cccd`: [build, unit/policy checks, phone/watch lint and APK publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41316038225) passed. [Wear OS 4 / API 33 verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41316038217) passed all seven focused scenarios at both the default round-watch density and approximately 192dp width. Coverage includes injected native rotary events, anticlockwise Snooze and clockwise Dismiss, unchanged three-/six-unit thresholds, outward aura growth with fixed touch targets, pause/reversal resets, selected-duration preservation and 5–60 limits, 130% font scale, touch fallback and real AlarmManager/receiver delivery. No Orbit crashes were observed. Actual-display frames were reviewed internally. [Phone ringing visual-cue regression](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41316038243) also passed.

Physical Galaxy Watch 4 Classic detents, direction mapping, haptics and on-device reliability still require device confirmation. The development signing identity and alarm record schema are unchanged; update over the installed app to preserve records.

The watch harness waits for MainActivity to be focused, laid out and configuration-stable before creating a short timer through production Store.save. Fresh Wear OS images can keep a TrayInitializationOverlay (Starting...) above the app after boot_completed; the final successful compact run needed about 21 seconds, beyond the earlier 15-second setup cap. Readiness is bounded at 90 seconds; actual alarm delivery still has its own unchanged wait. Earlier raw-preference fixtures chosen before cold startup were canceled by repeated restore calls around the overdue deadline, so they were replaced with the normal save path after readiness. No production scheduler change was made as part of this UI work; cold-start/reboot/near-deadline alarm reliability remains a separate acceptance area. The test probe is a separately packaged same-key instrumentation APK, never bundled into the published apps.


## Watch exact-alarm permission — release 0.1.45

Release **0.1.45** (version code **46**), source `c213a5afabdf1b03bc4c87f0c2316d9c44078160`: [build, unit/policy checks, phone/watch lint and APK publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41310315805) passed. [Wear OS 4 / API 33 verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41310315905) passed the runtime scenarios: fresh automatic exact-alarm access; the previous APK denied access; an in-place update preserving and rescheduling the saved alarm; and a UI-created one-minute timer delivered to the foreground ringing service with no Orbit crash. No exact-alarm appop grant is applied to the fixed APK. Both merged manifests are checked to keep the phone permission flow unchanged.

The emulator is a Wear OS image, not Samsung's physical device. The user's reported 6.0 may be One UI Watch; precise device OS mapping and physical-watch acceptance remain unverified. The test environment simulates unplugged/discharging battery state so Wear OS's charging activity cannot obscure Orbit. Ringing service detection accepts Android's abbreviated ComponentName.

The watch manifest caps SCHEDULE_EXACT_ALARM at API 32 and declares USE_EXACT_ALARM. Shared scheduling code, alarm storage, package ID and development signing key are unchanged. The published assets are `orbit-clock-phone-debug.apk` and `orbit-clock-watch-debug.apk`; only the watch update is required for this permission fix.

## Previous low-contrast atmosphere and actionable banners

The latest user preference supersedes the earlier visual-guidance rollout: shimmer belongs only on the ringing slider. Main-tool shimmer bindings are removed and VisualHints rejects non-ring keys. Keep ordinary native navigation/press feedback, static edit/remove icons and no prose tips.

Ringing type is explicit: weight 350 for the large clock, 400 for the 24sp label, 500 for duration, and 450 for 22sp Snooze/Stop labels. The previous phone background was a static near-black gradient with a tiny local-time tint. That version blended through deep-night blue, dawn rose, morning sage, afternoon amber, evening violet and night indigo, with RGB channels <=20/255 and continuous midnight wrapping. The localized corner-light update above supersedes that appearance. Watch native dark colors remain unchanged.

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


## Media pause on ringing

The ringing service sends native Pause and requests transient exclusive USAGE_ALARM audio focus. Successful focus requests stay held across overlapping alarms until the last occurrence ends. Android 15 may deny focus to an alarm-triggered background FGS; the Pause command still operates and the fallback preserves the alarm tone. Delayed focus waits for gain, later losses are respected, and Orbit never forces media to resume. No new user permissions were added.

Verified source `1040392858c2a2f60c3e4c950a64136ec12a8797`: [build, seven unit tests, 54 policy checks, phone/watch lint and APK publication](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41320959971) passed. [Android 15 media emulator verification](https://github.com/vish9731-agentic/orbit-clock/actions/runs/41320960051) passed all **nine scenarios** with an empty crash buffer. Music received Pause and LOSS_TRANSIENT, stopped advancing, stayed paused across overlapping alarms and one Stop, then regained focus after the last Stop. Foreground video received Pause and stopped advancing despite a real background-focus denial; the alarm's USAGE_ALARM player remained started and unmuted. Snooze persisted its ten-minute deadline and unchanged saved default, cleaned up focus, and left the already-paused video paused. Independent fixture playback, not the actual YouTube app, was tested. Internal playback snapshots and audio dumps were downloaded and inspected; physical phone/watch audio remains untested.
