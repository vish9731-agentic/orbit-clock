Pomodoro / Focus mode preview 0.1.119 (version code 120), source ea52d0eb6d7f50a3c8b428f0fa6598cbfddce573.

Download the matching phone and watch APKs below. Install over the existing Orbit Clock app to retain saved alarms; the original development signing key is preserved. Install the watch APK on the watch separately. Open Timer → Focus mode.

Includes configurable focus/break cycles, Pause/Resume, confirmed End, notifications, clockwise bezel Start/Resume and anticlockwise Pause while the watch Focus screen is open. Existing widget work is preserved; Pomodoro widget integration remains deferred.

Validation: GitHub Actions build 41564872610 passed both APKs, all 12 unit tests, 82 core policy checks, 39 widget checks and phone/watch lint. Runtime run 41564872807 passed 17 Android 15 phone checks and 23 Wear OS API33 checks, including real one-minute background completion, notification controls, phase transitions, persistence, state convergence and native rotary events. Both APK signatures were verified against the existing development certificate. Physical watch direction/feel and real paired-device transport remain device acceptance checks.

This is a separate test release from PR #4; default-branch integration is pending explicit approval. Broad ringing verification is not claimed: the separate ringing workflow lost its emulator connections. Production alarm/ringing UI source was unchanged by Pomodoro.
