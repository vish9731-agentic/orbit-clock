# Reference-based interaction and guidance system

Keep the approved Google Clock ringing layout and quiet typography. Visual affordances must suggest an available action, and state feedback must reflect observed facts.

## References and application

| Reference | Applied pattern |
| --- | --- |
| [Material 3 motion](https://m3.material.io/styles/motion/overview/how-it-works) | Coordinated spatial transitions; short opacity/color effects; native Material component motion |
| [Reply motion examples](https://codelabs.developers.google.com/codelabs/material-motion-android) | Continuous context between tools, persistent chrome, shared-axis movement with overlapping fades |
| [Designing Fluid Interfaces](https://developer.apple.com/videos/play/wwdc2018/803/) | Direct finger tracking, raw-coordinate velocity, interruptible native spring return |
| [TipKit education principles](https://developer.apple.com/videos/play/wwdc2023/10229/) | Contextual eligibility, learning and a three-visible-process-session cap; no text coaching UI |
| [Android predictive back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) | Native back opt-in and Material bottom-sheet handling, with older-platform fallback |
| [Android haptics](https://developer.android.com/develop/ui/views/haptics/haptics-principles) | Normal system ticks for selection/threshold, confirmation on completion, no forced vibration preferences |

## Visual guidance

Explanatory coaching rows/captions are removed. Existing action labels, permission explanations, validation errors and accessibility descriptions remain.

- The ringing rail receives two outward light sweeps, suggesting both swipe directions without moving its thumb or changing touch behavior.
- Add, the first timer preset, the running stopwatch Lap action, sleep-goal and wake-up controls receive a brief soft highlight.
- Alarm times include a small pencil affordance. Cities include a visible 48dp remove icon with confirmation; long press remains available.
- No cue adds text, consumes input, changes layout, or delays an action. Each lasts 1.8 seconds after a 500ms delay, with no permanent loop.
- Cues require a focused, visible control and enabled animations. They cancel on interaction, loss of focus or detach, and after demonstrated learning.
- Each cue displays at most once per app process and across three eligible sessions. New visual preferences are separate from the retired prose-tip preferences.

## Motion and state

The activity header, navigation, FAB and status surface remain mounted while tool contents change. Ordered tools use native Material shared-axis movement with overlapping fades. Native FAB show/hide is preserved. Data refreshes retain keyed item movement and scroll position.

The slider tracks raw finger position/velocity, and a native SpringAnimation restores it after an incomplete gesture. New input cancels the spring at its current position. A bounded distance threshold commits actions on release; spring callbacks never act on alarms. Reduced motion restores the thumb immediately. Focus loss and detach cancel tracking/animation.

Save/delete/start/cancel feedback confirms local state changes. Explicit sync requests show a finite connection check, then a connection/request result or timeout. Neither a reachable node nor a successful Data Layer write is presented as a delivery acknowledgement. No periodic network polling or decorative loading loop is added.

## Verification

Use full emulator scope for this cross-app change. Focused guidance, ringing-motion, motion or visual-cue scopes are available for subsequent corrections after the unaffected flows are covered. Tests exercise real user interactions, process restart, visual cues, learning, actual alarm delivery, spring interruption, hardware snooze, native back, rapid navigation and reduced motion. Internal transition video checks need enough actual intermediate frames, not a fixed recording duration: static virtual displays may stop emitting frames when a scene settles.

The visual-cue scope records before real timer delivery and includes notification opening in its 40-second window, so internal review can see the actual two-pass shimmer. A shorter home-screen-only recording is not evidence of the visual effect.
