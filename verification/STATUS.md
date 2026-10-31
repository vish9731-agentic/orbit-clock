# Verification status

## Completed locally

| Check | Result |
| --- | --- |
| `:phone:assembleDebug` | Passed; `phone/build/outputs/apk/debug/phone-debug.apk` produced |
| `:wear:assembleDebug` | Passed; `wear/build/outputs/apk/debug/wear-debug.apk` produced |
| `:core:testDebugUnitTest` | 7 tests, 0 failures, 0 errors, 0 skipped |
| `bash tools/test-core.sh` | 34 production timing/timer/rotary-policy checks passed |
| `:phone:lintDebug` | 0 errors, 2 warnings |
| `:wear:lintDebug` | 0 errors, 3 warnings |
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
