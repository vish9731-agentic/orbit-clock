# Orbit Clock handoff

## Goal and authorization

The user wants a personal Android phone + Galaxy Watch 4 Classic clock app inspired by Google Clock, with shared alarms and rotary-bezel snooze/dismiss. They want to request changes from their phone and receive downloadable debug APKs without keeping a Mac running.

The user authorized creating the private personal repository, uploading this project, fixing build failures, running tests and publishing debug APK downloads. Login and account authorization are completed by the user. Do not use Bytebeam repositories for this project.

## Current state

- Private repository exists: https://github.com/vish9731-agentic/orbit-clock. Personal-account access and admin/push permissions were confirmed.
- Source lives at `/workspace/orbit-clock`. Modules: `core`, `phone`, `wear`. JDK 17, SDK 35, AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9; Gradle wrapper is included.
- Local build succeeded for `:core:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug`.
- 7 Kotlin/JUnit tests and 34 production Java policy checks passed. Lint: zero errors, 2 phone warnings and 3 wear warnings.
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
