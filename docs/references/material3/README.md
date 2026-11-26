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

- The clock leads at 36sp regular weight (32sp in short windows).
- The duration is a small, single-line 16sp readout such as “10 min”.
- +/- use 48dp filled targets with a centered readout, symmetric horizontal spacing and 8dp gaps between rows.
- Snooze/Stop use secondary-container tonal roles, 48dp touch targets and 14sp labels.
- The 12sp alarm label occupies the full footer width and wraps without ellipses. Very long content can scroll rather than being discarded.
- Padding is restrained: 14dp horizontally and 10dp vertically. Dark surfaces, rounded corners and paired Material color roles keep the card quiet.
- Wallpaper-derived dynamic colors apply when supported; the clock remains the largest text.
- The popup stays mounted during duration changes. Rotation uses current window bounds and system/cutout insets; a height-clamped viewport keeps content reachable.
- +/- only adjust the occurrence's duration; Snooze is a separate tap. Preserve 5–60 minute bounds and persistent-window behavior.
- The upstream documents include newer Expressive APIs. The app keeps Material 1.12 and uses compatible components/attributes.

These are Orbit-specific choices applying the user's compact, clock-first design and full-label readability requirements. They are not a claim that Google specifies this exact alarm layout. Actual portrait/landscape, long-label and enlarged-text results are recorded in verification/STATUS.md.
