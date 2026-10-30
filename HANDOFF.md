# Continue Orbit Clock setup

User wants a personal Android phone + Galaxy Watch 4 Classic clock app inspired by Google Clock, with reliable shared alarms and rotary-bezel snooze/dismiss. Long-term goal: request code changes from a phone and receive installable debug APKs, without keeping a Mac running.

## Authorized work
Create private repository vish9731-agentic/orbit-clock, outside Bytebeam. Upload this project, run cloud builds, fix failures, and deliver downloadable phone/watch debug APKs. User will personally complete login/authorization when necessary. Never use bytebeamio/syncup for this project.

## Existing work
Native Kotlin Android project: core, phone and wear modules. Alarm CRUD, local scheduling, Wear Data Layer sync, ringing service, rotary gesture controls, world clocks, timers, stopwatch and basic bedtime UI. This is a prototype, not full Google Clock parity. No integration into Google Clock's private alarm database.

30 standalone checks of the production Java timing and rotary policies passed with tools/test-core.sh. Saved results are in verification/. Android/Kotlin compilation, lint, app launch and device tests have NOT run. No APK has been produced.

.github/workflows/android-debug.yml prepares builds, tests, lint and debug-latest prerelease APK publication. It has NOT run. debug.keystore is a deliberately shared DEVELOPMENT key, with standard android passwords, for repeatable update signing; never use for production.

## Confirmed blockers in previous session
No browser-control tool was exposed. GitHub connector identity was vish9731-agentic, but its only visible installation was bytebeamio. Personal repository returned 404 (could mean absent or inaccessible). Ordinary shell networking was sandbox-denied. Requests for additional network permissions repeatedly stalled before execution. One require_escalated CLI request executed and GitHub rejected repository creation with HTTP 403 Resource not accessible by integration. User authorization to act is already explicit; do not keep asking for it. Missing service capabilities still require setup.

## Next steps
1. Inspect the actual tools and permissions in this new session. Do not assume browser control or personal-repository administration is available. Read applicable skills and AGENTS.md.
2. Use Mac local terminal and existing Android SDK/Gradle if accessible to compile, run tests and fix errors now. User says their Mac already has Android tooling.
3. With a supported browser-control tool, open GitHub and let the user sign in personally, then create the private personal repository. Alternatively use a properly authenticated local gh CLI. Never ask for credentials in chat.
4. Upload this project; validate and fix CI until both APKs build successfully. Verify stable signing and increasing version codes. Provide actual successful download links, not URL templates.
5. Verify that phone-accessible sessions can edit this repository, push changes, inspect CI and deliver artifacts. A shared chat history alone does not connect Mac tools. Explain the verified mobile workflow and its limits. GitHub Actions is the intended build service.
6. Android requires user installation approval. Watch APK installation needs separate Wear OS tooling/wireless ADB. Do not promise silent installation or physical-device tests without connected devices.

Keep attempts bounded and provide timely updates. Do not repeat stalled approval requests. Report actual results clearly.
