# Material 3 references for Orbit Clock

Downloaded 2026-11-26 from Google's official [Material Components for Android](https://github.com/material-components/material-components-android) repository. These are upstream Markdown documents, not an unofficial design “skill”. They are kept here as a learning/reference snapshot; they do not ship in either APK. Original Apache 2.0 license is included as LICENSE. Relative images/links in the upstream documents refer to that original repository.

| Local file | Official source | Source blob |
| --- | --- | --- |
| [Color.md](Color.md) | [Upstream](https://github.com/material-components/material-components-android/blob/master/docs/theming/Color.md) | `4a673ec57b8fb9e78ee56d9d5c57782b04602233` |
| [Typography.md](Typography.md) | [Upstream](https://github.com/material-components/material-components-android/blob/master/docs/theming/Typography.md) | `5e19fb48ca411b35381f9452b342bbba40a9b51f` |
| [Button.md](Button.md) | [Upstream](https://github.com/material-components/material-components-android/blob/master/docs/components/Button.md) | `8bc122b62de2c43f79e4fc1aac8bac5ec6279a3a` |
| [IconButton.md](IconButton.md) | [Upstream](https://github.com/material-components/material-components-android/blob/master/docs/components/IconButton.md) | `14c14850eb03f0f441f368261ea075f541a0c3de` |
| [LICENSE](LICENSE) | [Upstream](https://github.com/material-components/material-components-android/blob/master/LICENSE) | `d645695673349e3947e8e5ae42332d0ac3164cd7` |

## Applied to the alarm popup

The user's approved black/amber reference supersedes the earlier wallpaper-colored stacked popup. Material 1.12 components retain their native ripple, accessibility and press motion; no dependency upgrade is needed.

- The time leads at up to 52sp, weight 350, with automatic sizing. The duration stays a smaller, single-line 16sp readout.
- The charcoal duration capsule is 64dp tall, with **64×64dp minus and plus targets**. The entire endpoint zones are tappable, not just their glyphs.
- Amber Snooze and charcoal Stop form a right-hand column with 16sp labels. If measured, scaled text and target widths cannot fit, the actions move to a bottom row.
- A quiet 14sp alarm label spans the full footer width and wraps without ellipses. Very long content can scroll instead of being discarded.
- Black surface, 32dp corners, 16dp side/14dp vertical padding, 12dp column/footer gaps and 8dp paired-control gaps match the approved reference.
- Taps on the card's non-control areas open the existing full-screen alarm (RingActivity), for the same active occurrence and selected duration. Buttons own their touches, including disabled +/- endpoints. Scrolling and canceled drags do not navigate. Outside-card touches still reach the underlying app.
- The popup stays mounted during adjustment. Current window bounds, cutout/system insets, font scale, locale and time format drive rebuilding. Height-clamped scrolling keeps content reachable.
- +/- only adjust the occurrence's duration; Snooze remains a separate tap. Preserve 5–60-minute bounds, the granted overlay permission and 30-second auto-snooze.

These are Orbit-specific choices applying the approved design, not a claim that Google specifies this exact alarm layout. Exact emulator results are recorded in verification/STATUS.md.

## App-wide palette and texture

The approved palette now applies to all phone/watch tools, Focus, Material editors/dialogs, ringing controls and widget variants. Shared roles live in OrbitColors/OrbitPalette and the fixed dark Material theme: black #050505, charcoal #191919/#252525, warm white #F5F3F0, muted #C6C4C1 and amber #FFC15A with #382800 tonal containers. Both system modes use these roles. The ringing time-of-day atmosphere remains a deliberate accent over black.

OrbitHatchDrawable caches paths and shaders for fine diagonal hatching that fades toward a quiet edge, derived from the existing watch alarm rail. Selected cards, dials and control rails use it. Widget artwork composites the same detail through its existing scrim alpha; transparent clock-only mode, feathering and the dark-text preference remain. Retain native ripples, typography hierarchy, target sizes and existing motion. Exact build/runtime evidence belongs in verification/STATUS.md.
