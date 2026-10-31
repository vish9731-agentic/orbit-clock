# Orbit Clock development

Personal project owned by vish9731-agentic. Use only vish9731-agentic/orbit-clock; never use Bytebeam repositories for this project.

Read HANDOFF.md for the product goal, current state, known limitations and verification results before changing code. Keep it current when material behavior or setup changes.

Goal: native Android phone and Wear OS Galaxy Watch 4 Classic clock replacement, inspired by Google Clock, shared local alarms, two-way sync and focused-screen rotary bezel snooze/dismiss. Current implementation is a prototype; do not promise Google Clock parity or private system integration.

User wants to prompt from a phone and receive downloadable APKs. For an authorized change: implement, run relevant tests, commit/push through available repository access, inspect GitHub Actions, fix failures and return successful debug APK download links. Do not claim a workflow or build passed before checking. If write/build tools are unavailable, state the actual blocker.

Run `bash tools/test-core.sh` and `./gradlew :core:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug` for changes to alarms, scheduling, sync or build configuration. JDK17+, SDK35 and network access for dependencies are required. Preserve the shared DEVELOPMENT debug.keystore for update compatibility. It must never sign production releases.

Both variants share application ID and signing identity. Install phone variant on phone and wear variant on watch. Real-world ringing, Bluetooth delivery and physical bezel behavior need target-device testing; never infer these from compilation.

## Phone prompting and emulator feedback

The user will request changes from their phone. Implement the change, then use a cloud Android emulator to exercise and verify the requested behavior before reporting completion. A successful build or launch-only smoke check is insufficient evidence for a feature change: add or adapt scenario-specific interactions and assertions, inspect the resulting UI/logs, fix defects and rerun relevant checks.

Use screenshots, logs and test artifacts as internal feedback when useful. Do not routinely send the user downloadable screenshots, test reports or artifact links; summarize what was verified and any remaining limitation. Provide those materials when requested. Existing APK delivery remains available.

Use a Wear OS emulator for watch-specific runtime behavior where feasible. A phone emulator cannot establish watch sync, physical bezel behavior or real-device alarm reliability. State any untested requirement explicitly rather than claiming all requested behavior works.
