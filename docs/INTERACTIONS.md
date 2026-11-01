# Reference-based interaction and guidance system

Keep the approved Google Clock ringing layout and quiet typography on phone. Watch ringing uses its own circular action layout. Visual affordances must suggest an available action, and state feedback must reflect observed facts.

## References and application

| Reference | Applied pattern |
| --- | --- |
| [Material 3 motion](https://m3.material.io/styles/motion/overview/how-it-works) | Coordinated spatial transitions; short opacity/color effects; native Material component motion |
| [Reply motion examples](https://codelabs.developers.google.com/codelabs/material-motion-android) | Continuous context between tools, persistent chrome, shared-axis movement with overlapping fades |
| [Designing Fluid Interfaces](https://developer.apple.com/videos/play/wwdc2018/803/) | Direct finger tracking, raw-coordinate velocity, interruptible native spring return |
| [TipKit education principles](https://developer.apple.com/videos/play/wwdc2023/10229/) | Contextual eligibility, learning and a three-visible-process-session cap; no text coaching UI |
| [Android predictive back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) | Native back opt-in and Material bottom-sheet handling, with older-platform fallback |
| [Android haptics](https://developer.android.com/develop/ui/views/haptics/haptics-principles) | Normal system ticks for selection/threshold, confirmation on completion, no forced vibration preferences |

## Ringing-only visual guidance

The user now wants shimmer only on the ringing screen. All main-tool bindings are removed, and VisualHints rejects every key except ring_controls. Static edit/remove affordances remain; existing press, ripple, navigation and value feedback remain. No coach-tip rows or captions are restored.

The ringing rail emits two finite outward light sweeps, without moving its thumb or intercepting touch. Visibility, focus, learning, reduced-motion and lifecycle cancellation still apply. No other screen receives shimmer.

## Ringing type and atmosphere

The clock uses explicit weight 350, the smaller label uses 400, duration 500, and rail labels 450 at 22sp. The label is quieter than the clock. Hit targets and the selected rail layout remain.

RingAtmosphere interpolates through deep-night blue, dawn rose, morning sage, afternoon amber, evening violet and night indigo using local time. Every RGB channel stays at or below 20/255. A static near-black gradient carries only a faint lower-screen tint, with no illustrations or ongoing animation. Native dark watch colors remain unchanged.

## Alarm notification controls

The phone heads-up and expanded notification use a compact black surface with alarm label, five-minute decrement/increment selection and a Snooze/Stop action rail. The collapsed view preserves both actions. Android controls its system header, presentation, timing and custom-view size; notification controls are taps, not a draggable slider. No overlay permission is requested. Wear notifications expose native Snooze/Stop actions.

RingControls stores snooze selection by alarm ID and occurrence timestamp. Full-screen and notification controls read the same selection; saved alarm defaults remain unchanged. Immutable action PendingIntents have unique occurrence/action URIs and target a non-exported receiver. The receiver validates Scheduler.isActive before acting, so stale actions cannot affect a later occurrence. Notification updates are alert-once and do not open an activity for adjustment, snooze or stop.

## Motion and state

The activity header, navigation, FAB and status surface remain mounted while tool contents change. Ordered tools use native Material shared-axis movement with overlapping fades. Native FAB show/hide is preserved. Data refreshes retain keyed item movement and scroll position.

The slider tracks raw finger position/velocity, and a native SpringAnimation restores it after an incomplete gesture. New input cancels the spring at its current position. A bounded distance threshold commits actions on release; spring callbacks never act on alarms. Reduced motion restores the thumb immediately. Focus loss and detach cancel tracking/animation.

Save/delete/start/cancel feedback confirms local state changes. Explicit sync requests show a finite connection check, then a connection/request result or timeout. Neither a reachable node nor a successful Data Layer write is presented as a delivery acknowledgement. No periodic network polling or decorative loading loop is added.

## Verification

Use full emulator scope for this cross-app change. Focused guidance, ringing-motion, motion or visual-cue scopes are available for subsequent corrections after the unaffected flows are covered. Tests exercise real user interactions, process restart, visual cues, learning, actual alarm delivery, spring interruption, hardware snooze, native back, rapid navigation and reduced motion. Internal transition video checks require an actual intermediate scene blend and a settled destination, without assuming a virtual-display frame rate. Endpoint masking excludes incidental focus feedback.

The visual-cue scope records before real timer delivery and includes notification opening in its 40-second window, so internal review can see the actual two-pass shimmer. A shorter home-screen-only recording is not evidence of the visual effect.

## Watch ringing controls

Use larger 56dp circular duration buttons above small icon-only Snooze/Dismiss actions. Curved arrows and expanding radial auras provide graphical bezel cues without visible action words, instructions or percentages. Anticlockwise turns snooze after the existing three-unit amount; clockwise turns dismiss after six units. The watch normalizes the raw encoder axis into the unchanged RotaryGesture accumulator. Intercept watch rotary events before scroll dispatch so large type never consumes an action detent. Touch targets remain fixed at 48dp while the visible cores and auras expand. Keep the chosen 5–60 minute duration, limits and occurrence-only selection semantics. Native accessibility labels remain available. Phone controls retain their existing rail.
