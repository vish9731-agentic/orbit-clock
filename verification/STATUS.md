# Verification status

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
