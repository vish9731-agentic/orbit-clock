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

## Material 3 design references

For native UI work, read [the project design mapping and official Markdown references](docs/references/material3/README.md). They include Google's color, typography and button guidance with source blob IDs and license. Match the current library version; newer Expressive examples in the reference snapshot do not authorize an untested dependency upgrade. The approved popup uses black/charcoal with amber Snooze, time first/largest, a smaller duration, full wrapping labels and symmetric 64dp +/- touch zones. Use paired columns when measured widths fit and a bottom action row otherwise. Card/time/label/duration-center taps open the existing full-screen RingActivity for the exact active occurrence, retaining selection. Action and disabled-button touches must not navigate; drags/scrolls must not become taps. Outside-card touches remain available to the underlying app.

## Shared black and amber theme

The approved popup palette now applies across phone, watch, Focus, editors/dialogs, ringing controls and widgets. Use OrbitColors/OrbitPalette and the fixed Material theme; do not reintroduce wallpaper DynamicColors or a light-theme branch without a new user request. Preserve the time-of-day ringing atmosphere as an intentional subtle accent. Reuse the cached OrbitHatchDrawable for native surfaces and OrbitHatch.draw for one-shot widget artwork. Hatch detail belongs at a quiet edge or selected hero/control surfaces, never over all content. Preserve widget transparency/feathering, explicit dark-clock-text preference, responsive layouts and touch regions. Theme changes must not alter alarm or sync behavior.

Framework android:textColorPrimary and android:textColorSecondary must reference the named ColorStateList resources, including disabled states. Material1.12 navigation loads their resource IDs; literal colors here caused the verified build151 startup crash. Watch headings must remain inside the physical round display, with centered single-line autosizing and chord-safe insets. Preserve strict native glyph/contrast checks when maintaining the theme harness; use independent drawable copies for raster feedback, never mutate live mounted backgrounds.
