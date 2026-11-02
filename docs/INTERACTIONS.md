# Reference-based interaction and guidance system

Keep the approved Google Clock ringing layout and quiet typography on phone. Watch ringing uses a compact version of the same rail with native rotary control. Visual affordances must suggest an available action, and state feedback must reflect observed facts.

## References and application

| Reference | Applied pattern |
| --- | --- |
| [Google Phone incoming-call announcement](https://support.google.com/phoneapp/thread/351208139/a-new-material-3-expressive-experience-in-the-phone-by-google-app?hl=en-GB) | Horizontal call-control reference; adapted outward glints, not a claim of Google's exact private shimmer curve |
| [Android spring physics](https://developer.android.com/develop/ui/views/animations/spring-animation) | Measured release velocity, damped inertia and interruptible native spring motion |
| [Material 3 motion](https://m3.material.io/styles/motion/overview/how-it-works) | Coordinated spatial transitions; short opacity/color effects; native Material component motion |
| [Reply motion examples](https://codelabs.developers.google.com/codelabs/material-motion-android) | Continuous context between tools, persistent chrome, shared-axis movement with overlapping fades |
| [Designing Fluid Interfaces](https://developer.apple.com/videos/play/wwdc2018/803/) | Direct finger tracking, raw-coordinate velocity, interruptible native spring return |
| [TipKit education principles](https://developer.apple.com/videos/play/wwdc2023/10229/) | Contextual non-blocking guidance without coaching prose; the requested ringing cue is finite and eligible for each new ringing view |
| [Android predictive back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) | Native back opt-in and Material bottom-sheet handling, with older-platform fallback |
| [Android haptics](https://developer.android.com/develop/ui/views/haptics/haptics-principles) | Normal system ticks for selection/threshold, confirmation on completion, no forced vibration preferences |

## Ringing-only visual guidance

The user now wants shimmer only on the ringing screen. All main-tool bindings are removed, and VisualHints rejects every key except ring_controls. Static edit/remove affordances remain; existing press, ripple, navigation and value feedback remain. No coach-tip rows or captions are restored.

The ringing rail emits two soft, finite outward glints after 450ms, across 1.8 seconds. The cue is eligible on each new visible phone ringing rail, even when the previous hint has been learned; touching cancels it immediately. Cached drawing objects avoid frame allocations. It does not move the thumb, intercept touch or dispatch actions. Visibility, focus, reduced motion and lifecycle cancellation apply. No other screen receives shimmer.

## Ringing type and atmosphere

The clock uses explicit weight 350, the smaller label uses 400, duration 500, and rail labels 450 at 22sp. The label is quieter than the clock. Hit targets and the selected rail layout remain.

The phone main field stays black with localized upper-corner light for dawn, morning sunshine, noon, afternoon, sunset, blue hour and night. Daylight has faint cloud wisps; 21:00 includes faint stars, and deep night includes sparse stars and a small crescent. The background follows current local time on each visible minute, resume and clock/time-zone change, including later snoozed occurrences. There is no background animation loop. Native watch colors remain separate.

## Alarm notification controls

The phone heads-up and expanded notification use a compact black surface with alarm label, five-minute decrement/increment selection and a Snooze/Stop action rail. The collapsed view preserves both actions. Android controls its system header, presentation, timing and custom-view size; notification controls are taps, not a draggable slider. No overlay permission is requested. Wear notifications expose native Snooze/Stop actions.

RingControls stores snooze selection by alarm ID and occurrence timestamp. Full-screen and notification controls read the same selection; saved alarm defaults remain unchanged. Immutable action PendingIntents have unique occurrence/action URIs and target a non-exported receiver. The receiver validates Scheduler.isActive before acting, so stale actions cannot affect a later occurrence. Notification updates are alert-once and do not open an activity for adjustment, snooze or stop.

## Motion and state

The activity header, navigation, FAB and status surface remain mounted while tool contents change. Ordered tools use native Material shared-axis movement with overlapping fades. Native FAB show/hide is preserved. Data refreshes retain keyed item movement and scroll position.

The slider tracks raw finger position/velocity. Its pill stretches horizontally with a gently thinning profile while its alarm glyph is counter-scaled to stay undistorted; rotation stays zero. A native SpringAnimation restores it after an incomplete gesture, carrying release velocity with damped overshoot. A separate retargetable spring controls elastic deformation. Edge tension stays bounded inside the rail. New input cancels the spring at its current position. The existing 70% threshold commits actions only with physical movement beyond native touch slop toward the selected action. Catching/tapping or pulling back an off-centre thumb does not act; spring callbacks never act on alarms. Destination labels fade before the stretching pill overlaps their letters. Reduced motion restores the thumb immediately. Focus loss and detach cancel tracking/animation.

Save/delete/start/cancel feedback confirms local state changes. Explicit sync requests show a finite connection check, then a connection/request result or timeout. Neither a reachable node nor a successful Data Layer write is presented as a delivery acknowledgement. No periodic network polling or decorative loading loop is added.

## Verification

Use full emulator scope for this cross-app change. Focused guidance, ringing-motion, motion or visual-cue scopes are available for subsequent corrections after the unaffected flows are covered. Tests exercise real user interactions, process restart, visual cues, learning, actual alarm delivery, spring interruption, hardware snooze, native back, rapid navigation and reduced motion. Internal transition video checks require an actual intermediate scene blend and a settled destination, without assuming a virtual-display frame rate. Endpoint masking excludes incidental focus feedback.

The visual-cue scope records before real timer delivery and includes notification opening in its 40-second window, so internal review can see the actual two-pass shimmer. A shorter home-screen-only recording is not evidence of the visual effect.

## Watch ringing controls

The watch uses the compact production rail with its larger duration controls, native typography and existing anticlockwise Snooze / clockwise Dismiss direction. Existing three-/six-unit amounts and per-occurrence duration semantics remain. Retargetable spring rendering follows detents, but physical input alone commits actions; touch can interrupt animation without jumping. The pill may stretch while its glyph remains undistorted. No on-screen coaching text or percentages are added. Physical Watch 4 Classic feel still needs device acceptance.
