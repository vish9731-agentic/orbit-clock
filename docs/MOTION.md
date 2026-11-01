# Orbit Clock motion and typography

Preserve the user-selected Google Clock ringing layout: near-black with a very subtle local-time tint, large time, saved label, compact duration row, and Snooze/Stop rail. Motion adds feedback to that layout; it does not introduce decorative clock faces or cards.

## Typography and proportions

Ringing time uses explicit weight 350 with tabular numerals and restrained tracking. The label is 24sp at weight 400, action labels are 22sp at weight 450, and the duration is 16sp at weight 500. Plus/minus controls retain 48dp hit areas on phone. The rail is 80dp high, with a 96 × 64dp thumb. Large text remains supported; alarm labels can wrap to two lines.

Main clock, timer and stopwatch numerals use tabular figures to avoid horizontal jitter. Existing Material type roles remain intact; ringing uses its own near-black atmosphere and restrained mint actions.

## Interaction inventory

| Interaction | Feedback |
| --- | --- |
| Buttons and clickable alarm cards | Native ripple plus 0.97 press scale over 80ms; 180ms return |
| FAB | Native Material press/elevation and show/hide motion |
| Tool navigation | Persistent chrome; native Material shared-axis movement and overlapping fades over 240ms, following tab order |
| Screen and card entrance | 10dp movement/fade; 18ms stagger capped at 90ms, limited to the first eight children |
| Alarm, timer, city or lap insertion | New visible item enters; existing keyed items move to their new positions over 220ms |
| Removal/reordering | State changes immediately; remaining visible keyed items move into place, preserving scroll position |
| Alarm toggles | Native Material thumb/track animation and a light tick; visual refreshes coalesce for 180ms so the thumb can finish |
| Weekday selection | Selected state updates immediately; foreground/background interpolate for 150ms |
| Timer presets | Selected preset changes color; displayed value gives a subtle 180ms pulse |
| Time-picker confirmation | Updated time gives the same value pulse |
| Invalid timer/snooze input | Material inline error caption appears immediately with a small 4dp, 180ms field nudge; error clears on correction |
| Stopwatch Start/Pause/Reset | Immediate state change with a subtle face pulse |
| Running stopwatch | Requests 16ms progress updates; uses monotonic elapsed time rather than animation timing |
| Paused stopwatch | No repeated unchanged text or dial invalidation; idle ticker slows down |
| New lap | Latest row entrance and keyed reflow of existing laps |
| Bedtime goal change | Immediate saved value, followed by a hero pulse |
| Alarm/permission/time bottom sheets | Native Material sheet movement; bounded content entrance for editor/time picker |
| Dialogs, switches, text fields and time picker | Native Material feedback retained, including focus, errors, ripple and checked state |
| City selection and watch-tool menu | Selection tick plus the corresponding content transition |
| Sync request | Finite 300ms half-turn and press feedback; bounded connection-status check, never falsely reports successful delivery |
| Ringing-screen entrance | Time and label fade/move in; alarm action controls are immediately available |
| Snooze-duration adjustment | Value changes immediately, then gives the 180ms value pulse |
| Slider drag | Thumb follows the finger directly, grows by 4%, tilts up to 8°, and gently dims the opposite action label |
| Slider threshold | One tick when crossing the threshold per gesture; confirmation haptic on completion |
| Visual affordances | Two finite outward light sweeps only on the ringing rail; no main-tool shimmer, instructional captions or layout movement |
| Incomplete/canceled slider drag | Native, velocity-aware spring return with bounded travel; labels follow actual spring position and new input interrupts without a jump |
| Slider completion and volume buttons | Alarm action executes immediately, never waits for animation completion |

## Motion policy

`OrbitMotion` centralizes finite easing and durations. No decorative looping animation or animation of every countdown digit is added. Offscreen items do not receive reflow effects. Running stopwatch updates remain essential time information, not an animation-based clock.

Custom motion checks `ValueAnimator.areAnimatorsEnabled()`. With Android animations disabled, values, selections, visibility and slider position resolve immediately. Native property animations also follow the system animation scale. Haptics use normal view feedback and honor device preferences.

Animations cancel on detach; slider motion also cancels on focus loss. New input interrupts prior feedback instead of queuing effects. Motion end callbacks never change alarm state or dispatch user actions. Keyed layout callbacks reject stale renders. Screen refresh coalescing affects presentation only: model writes, scheduling and sync are not delayed. Pending refresh/tick callbacks are removed on pause.

## Verification

The cloud emulator suite enables real Android animations, exercises normal feature flows, records a static Timer-to-paused-Stopwatch transition for 12 seconds, checks for a visible content blend between source/destination and a settled destination, and emits an internal filmstrip for visual review. It also tests rapid navigation, rapid duration adjustment, preset selection, invalid input recovery, short-slider cancellation with motion enabled/disabled, volume snooze with motion disabled, and editor/stopwatch actions with all animation scales set to zero.

See verification/STATUS.md for the observed result and source revision. Emulator feedback cannot establish physical-device frame rate, haptic quality or watch usability.

Verification scope defaults to full. After the main flows have been covered on unchanged app code, select ringing-motion or motion for a focused rerun using the workflow input or explicit commit markers [ui:ringing-motion] / [ui:motion]. Focused tests retain the same assertions. Scheduled-deadline reads wait up to five seconds for the main screen’s asynchronous restore to finish; a missing or wrong final schedule still fails.

See [INTERACTIONS.md](INTERACTIONS.md) for reference mapping, visual affordances, visible-session caps, native back behavior, and truthful connection feedback. Visual light sweeps use up to three focused, visible app-process sessions and retire after use. No explanatory tip text or coach-mark rows are added. Native page transitions keep the header/navigation/status/FAB mounted. Confirmation feedback reflects local saves and never asserts watch delivery without an acknowledgement.

The capture check masks pixels that differ between the two tools and requires a visible intermediate scene distinct from both endpoints, followed by a settled paused Stopwatch. It does not treat a virtual display’s number of emitted frames as a physical-device frame-rate guarantee. Input-focus color changes cannot substitute for a scene blend.

## Watch bezel feedback

Watch ringing uses small icon-only circular actions with fixed native 48dp touch targets. Their 32dp visible cores expand toward 44dp as direct bezel progress increases, surrounded by three radial aura bands that grow on both sides. Curved directional arcs indicate anticlockwise Snooze and clockwise Dismiss. Each side's progress is independent; reversal switches the active side and idle feedback clears after the existing gesture pause window. Bezel progress does not animate View scale or run looping shimmer, visible instructions or percentage labels; rendering never dispatches an alarm action. Native duration-button press and value-change feedback remain. Reduced-motion mode retains direct input feedback. Larger 56dp circular +/- controls keep the 5–60 minute occurrence selection above the actions. The phone layout and rotary thresholds are unchanged.
