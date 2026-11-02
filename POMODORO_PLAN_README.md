# Pomodoro / Focus Mode — saved planning notes

Status update 2026-11-02: user subsequently instructed "Proceed and build it." Phone/watch implementation is in draft PR #4, with local builds/tests/lint passed and final runtime verification blocked by GitHub Actions billing; widget integration remains deferred pending confirmation. See docs/FOCUS.md and HANDOFF.md for current implementation and verification. The original planning notes below are historical, not a restriction on the subsequent build authorization.

## User intent and next step

Add a seamlessly integrated Pomodoro experience to Orbit Clock on Android phone and Galaxy Watch 4 Classic. The user is currently building an existing widget and refining its UI and interactions. Wait until the user returns and explicitly requests integration. At that time, inspect the finished widget and current app code first, then adapt this feature to the widget's established theme, layout and functionality. Do not build a competing widget or prescribe a final widget layout from these notes.

This document records the discussion, not a completed technical specification or authorization to implement. No Pomodoro code is implemented by this documentation change. Once the widget is ready, complete product discovery and provide the detailed product and technical implementation brief the user requested before starting development.

## Proposed app experience

- Add Focus Mode within the existing Timer experience, subject to reviewing the current UI.
- Suggested configurable defaults: 25-minute focus, 5-minute short break, 15-minute long break after four completed focus sessions.
- Show the current phase, remaining time, circular progress and completed-session count.
- Provide Start, Pause, Resume and End controls.
- At a focus boundary, offer Start Break; at a break boundary, offer Start Focus. Manual transitions were recommended; optional automatic cycles and precise defaults remain to be confirmed.
- Use gentle transition sound/vibration, with final alert preferences to be decided.
- Preserve active session state across navigation and process recreation; support reliable background completion and notification controls.
- Detailed productivity charts and task lists are outside the proposed initial scope.

## Galaxy Watch 4 Classic — accepted bezel interaction

The user explicitly wants the physical bezel to control sessions and accepted this mapping on the visible Focus screen:

- Short deliberate clockwise rotation: Start when ready, Resume when paused, or start the offered next phase after completion.
- Short deliberate anticlockwise rotation: Pause a running session.
- Show visual feedback during rotation and a small haptic confirmation when an action commits.
- Accumulated deliberate rotation must cross a tested threshold. Continued rotation must not repeatedly trigger actions; require a stop/reset before rearming.
- Retain accessible touch controls.
- Do not assume global bezel interception while another app or the watch face is visible.

The user also mentioned stopping via the bezel. A distinct End/Stop gesture was not settled; resolve it in the final brief, including accidental-end protection. Do not silently map pause to destructive cancellation.

The watch should show a glanceable circular countdown, phase and session count, offer transition actions and permit starting without the phone. Verify physical direction, detents, thresholds and haptics on the actual Watch 4 Classic; emulator input alone cannot establish hardware behavior.

## Existing widget — desired capabilities, layout deferred

The user said these capabilities sound right, but requires inspection of their finished widget before designing integration:

- Ready: show the configured focus duration and Start Focus.
- Running: show Focus/Break, time remaining, session progress where space allows, and Pause.
- Paused: show remaining time and Resume.
- Phase complete: show completion and Start Break or Start Focus as appropriate.
- Main content can open the full Focus screen for settings and ending a session, adapted to existing widget interactions.
- Widget, app and watch control the same logical session: starting from the widget and pausing from the watch should be reflected across connected surfaces.

Widget type, existing implementation, supported sizes, update behavior and design are not yet inspected or confirmed. The discussion assumed an Android home-screen widget; confirm from the finished implementation rather than treating that assumption as a requirement. Match its established theme and functionality.

## Technical direction to investigate before implementation

Review current code and repository instructions; the prior discussion used README-level context rather than a full implementation audit.

- Use a shared session model/state machine for ready, running, paused and phase-complete states, with explicit focus/short-break/long-break phases.
- Persist session identity, phase, durations, completed-cycle count, running deadline or paused remainder, and synchronization metadata as required by the final design.
- Derive countdowns locally from deadlines rather than transmitting one update per second. Define monotonic timing and reboot recovery explicitly.
- Extend the existing Wear Data Layer infrastructure after reviewing its alarm-specific assumptions. Define ownership, command ordering, deduplication, stale-state rejection and reconnect conflict handling.
- A session must continue on its owning device when disconnected. Define how standalone watch starts and ownership transfer work before promising behavior across disconnects.
- Watch-preferred gentle alerts with phone fallback were proposed, not finalized. Acknowledge that network partitions complicate duplicate-free alerts; specify and test the actual fallback policy.
- Choose Android-supported widget countdown rendering and event-driven updates compatible with the finished widget implementation and target API levels.
- Review background execution, exact scheduling, notifications and permissions on both platforms. Keep existing alarm/timer behavior intact.

## Future validation and brief

Before coding, deliver a concrete product and technical brief covering finished-widget integration, screen states, bezel thresholds and Stop behavior, transition policy, session ownership and offline rules, persistence, scheduling, notifications, accessibility, implementation files and acceptance criteria.

Validate focus/break cycles, pause/resume from each supported surface, duplicate/stale commands, disconnect/reconnect, background delivery, process recreation, reboot policy, permission loss and coexistence with alarms/timers. Test phone and Wear OS runtime flows and the final widget's supported layouts. Physical-watch checks remain necessary for bezel feel and reliability.

Resume instruction: when the user says the widget is ready, inspect its current theme and behavior, reconcile these notes with the latest repository, and prepare the detailed brief. Do not start implementation merely because this planning file exists.
