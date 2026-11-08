# Focus mode / Pomodoro

## Product behavior

Open Timer → Focus mode on phone or watch. Defaults are 25 minutes focus, 5 minutes short break and 15 minutes long break after every fourth completed focus. Before starting, Durations accepts 1–180 minutes for each phase. Completion waits for Start break / Start focus; cycles never advance unattended. Pause preserves the remainder. End requires confirmation and resets cycle progress while retaining durations.

The countdown ring uses the existing Orbit palette. Phone and watch have touch controls. On the focused watch screen, three accumulated raw negative rotary units (clockwise, matching the current Orbit mapping) start/resume/advance; three positive units pause. Reversal clears incomplete accumulation. A 600ms pause rearms the gesture; continuous movement cannot toggle repeatedly. Visual arc feedback and native haptics acknowledge the gesture. End stays a confirmed touch action, since a separate safe bezel-stop gesture was never agreed. Physical Watch 4 Classic direction and detents need acceptance testing.

Existing square and wide widgets are intentionally unchanged, honoring the earlier request to integrate only after their finished design is reviewed and scope confirmed.

## Architecture and timing

`FocusSession` is a pure state machine. `FocusStore` persists a separate JSON record in private preferences, schedules an explicit FocusReceiver pending intent and exposes the same actions to UI and notifications. Existing alarm records, ringing controls and signing remain unchanged.

The device starting/resuming a phase owns its completion alarm. The owner uses an elapsed-realtime deadline persisted with Android boot count, so manual wall-clock changes do not alter an in-progress phase on that device. After reboot, the saved wall deadline reconstructs remaining time; expired phases complete once, without advancing further. Force-stop prevents background delivery until reopening, as with Android alarms generally. Without exact-alarm access, Android can delay idle delivery; the UI explains this and offers settings or an explicit inexact start. Notification permission is requested contextually; users may start without alerts.

Low-importance progress notifications expose Pause/Resume and a native countdown. Phase completion uses a separate default-importance sound/vibration channel and offers the next phase. Android notification settings and Do Not Disturb govern actual presentation. No new foreground service or polling wake lock is added.

## Two-way live synchronization and watch display

Every local focus edit, start, pause, resume, phase completion and End sends a signed Wear MessageClient snapshot immediately to connected nodes, alongside the existing durable DataItem fallback. A correlated receipt contains the receiver's authoritative revision, so either direction can reconcile a newer peer state. Initial app refresh exchanges even an empty local record to pull an already-running peer session. Newer state wins; stale/replayed packets cannot reset it, and incoming packets do not echo indefinitely or launch screens. A shared versioned capability prompts reconciliation after reconnection. Both updated APKs are required for receipts.

Phone and watch show sync state and offer Sync again when unavailable or unconfirmed. A successful local DataItem write alone is not labeled remote confirmation. The phone's Timer entry also displays an active Focus session started on the watch, while an already-open Focus screen refreshes immediately in either direction.

While the watch Focus Activity is foreground and a countdown is Running, FLAG_KEEP_SCREEN_ON prevents normal display timeout. Pause, phase completion, End and leaving the screen release it. An authorized phone handoff requests screen wake and opens Focus; screen pinning/device-owner/kiosk mode is not used. Wear OS may still show lock/system UI or honor Home; no ordinary app can promise to prevent that. Keeping the active display on consumes additional watch battery. Wrist-down/ambient and return-to-watch-face behavior need physical Watch 4 Classic acceptance.

## Phone-to-watch screen handoff

Starting/resuming a phase on the phone now initiates a separate live handoff. The phone discovers a reachable watch (preferring `orbit_focus_handoff_v1` and probing connected nodes if capability propagation lags installation), sends the complete session through the signed Wear MessageClient transport, and waits for the watch to persist and acknowledge that exact revision. Only then does AndroidX RemoteActivityHelper send the browsable launch URI to that node. Both updated APKs are needed; older watches can still receive ordinary state synchronization but cannot perform the new screen handoff.

The URI contains only a random short-lived ticket. It cannot import a record or modify a timer. The watch-only exported router validates the ticket against the prepared session, boot count and elapsed-time expiry, consumes it once, and opens the non-exported FocusActivity. Focus requests screen wake only for that authorized handoff and reports visibility after its window actually has focus. It does not bypass the watch lock screen. The phone displays “Watch opened Focus” only after the matching node/nonce visibility acknowledgment, never merely because an intent was sent. Unsupported/blocked launch, an old watch APK, no connection, or a missing acknowledgment instead produce a status and an Open on watch retry. Retry does not restart the timer.

Pause, End and ordinary Data Layer replays do not force a screen open. A newer session edit invalidates pending tickets. Reconnection continues durable state sync without reopening an old request. The remote API is subject to Wear OS availability, watch lock state and pairing; it cannot guarantee screen presentation when the OS refuses it.

## Synchronization and offline limits

A separate `/orbit/focus/session` Wear Data Layer register carries the full record, including logical revision and writer UUID. Local writes use max(wall time, previous revision + 1); incoming records compare revision then writer deterministically. Stale/replayed records are ignored. Data Layer is eventually consistent; there is no per-second network traffic. The session record is committed before scheduling, notifying or sending it. Background sync reception schedules or cancels the local deadline according to ownership.

Only the owner emits the phase-completion alert. This initial policy avoids routine double alerts and does not implement automatic watch-preferred routing or a disconnected-device fallback. A remote Pause/End cannot cancel an offline owner's alarm until delivery of that command. Concurrent offline edits resolve deterministically and may discard one action. Do not advertise immediate offline remote control or exactly-once alerts under network partitions. Non-owner countdowns use the shared wall deadline and assume reasonably aligned device clocks; clock skew can affect their display until state converges. Bluetooth delivery, reconnection and actual hardware haptics need physical-device validation.

## Validation

Unit coverage checks interruption remainder, duplicate transitions, unattended completion, four-session long breaks, End preference retention and deterministic write ordering. The separate same-key FocusProbe uses production controls, real one-minute AlarmManager delivery in the background, notification inspection, recreation persistence and native Wear rotary MotionEvents. The Focus workflow builds both APKs, runs unit/policy checks and both lints, then executes Android 15 phone and Wear OS API33 scenarios. Refer to HANDOFF/verification status for actual run outcomes; this design document alone is not proof that checks passed.

Presets, pre-break-end warnings, historical daily totals, automatic cycles and widget integration remain future options, rather than silently added scope.

Handoff tests validate exact-snapshot/boot/expiry policy, a real watch URI launch from home after a prepared incoming message, one-use routing, rejected unprepared/stale/late launches, pause invalidation, and phone retry without a timer restart. The isolated emulator fixtures exercise the trusted message boundary directly; they do not establish real Bluetooth pairing or end-to-end MessageClient/RemoteActivityHelper transport between physical devices. That needs acceptance on the user's paired phone and Watch 4 Classic.
