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

Private repository `vish9731-agentic/orbit-clock` exists and personal admin/push access was confirmed. The workflow is configured for tests, lint, both debug APKs and `debug-latest` prerelease publication. The first cloud run, uploaded artifacts and release assets have not yet been verified; update this section once confirmed.

## Not yet tested

No Android device/emulator launch or instrumentation has run. Bluetooth/Wear Data Layer delivery, alarm service behavior under Android power/permission policies, full-screen presentation, watch UI usability, physical rotary-bezel direction/thresholds and signed updates remain device acceptance checks. Passing build, lint and policy tests does not establish alarm reliability or complete Google Clock feature parity.
