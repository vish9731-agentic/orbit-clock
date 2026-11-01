# Reference-based interaction and guidance system

Keep the approved Google Clock ringing layout and quiet typography. Hints must explain an available action, and state feedback must reflect observed facts.

## References and application

| Reference | Applied pattern |
| --- | --- |
| [Material 3 motion](https://m3.material.io/styles/motion/overview/how-it-works) | Coordinated spatial transitions; short opacity/color effects; native Material component motion |
| [Reply motion examples](https://codelabs.developers.google.com/codelabs/material-motion-android) | Continuous context between tools, persistent chrome, shared-axis movement with overlapping fades |
| [Designing Fluid Interfaces](https://developer.apple.com/videos/play/wwdc2018/803/) | Direct finger tracking, raw-coordinate velocity, interruptible native spring return |
| [TipKit education principles](https://developer.apple.com/videos/play/wwdc2023/10229/) | Contextual eligibility, learning, dismissal, and a three-visible-session cap; implemented natively on Android |
| [Android predictive back](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back) | Native back opt-in and Material bottom-sheet handling, with older-platform fallback |
| [Android haptics](https://developer.android.com/develop/ui/views/haptics/haptics-principles) | Normal system ticks for selection/threshold, confirmation on completion, no forced vibration preferences |

## Guidance

- Empty alarms explain that + creates an alarm. Disabled alarms explain how to schedule one.
- Saved alarms explain tap-to-edit; opening an existing editor retires that hint.
- World clocks explain long-press removal; confirmed removal retires it.
- Timer presets/custom entry are explained beneath setup controls; using either retires that hint.
- Stopwatch guidance sits below Start/Pause and Lap/Reset, so learning cannot move those controls under a finger.
- Bedtime explains the relationship between sleep goal and wake-up time.
- Watch alarm pages can explain bezel control without adding another row to the compact ringing controls.
- Phone ringing first explains slide directions, then offers the volume-button shortcut after touch controls are learned. Successful hardware use retires its hint.

Hints stay local to each device. Explicit dismissal and demonstrated learning take precedence over display limits. Only hints at least half-visible count toward the session budget; rotations and page rerenders do not consume additional impressions. Essential action labels and accessibility instructions remain after education retires. No modal coach marks interrupt a ringing alarm.

## Motion and state

The activity header, navigation, FAB and status surface remain mounted while tool contents change. Ordered tools use native Material shared-axis movement with overlapping fades. Native FAB show/hide is preserved. Data refreshes retain keyed item movement and scroll position.

The slider tracks raw finger position/velocity, and a native SpringAnimation restores it after an incomplete gesture. New input cancels the spring at its current position. A bounded distance threshold commits actions on release; spring callbacks never act on alarms. Reduced motion restores the thumb immediately. Focus loss and detach cancel tracking/animation.

Save/delete/start/cancel feedback confirms local state changes. Explicit sync requests show a finite connection check, then a connection/request result or timeout. Neither a reachable node nor a successful Data Layer write is presented as a delivery acknowledgement. No periodic network polling or decorative loading loop is added.

## Verification

Use full emulator scope for this cross-app change. Focused guidance, ringing-motion or motion scopes are available for subsequent corrections after the unaffected flows are covered. Tests exercise real user interactions, process restart, display caps, dismissal/learning, actual alarm delivery, spring interruption, hardware snooze, native back, rapid navigation and reduced motion. Internal transition video checks need enough actual intermediate frames, not a fixed recording duration: static virtual displays may stop emitting frames when a scene settles.
