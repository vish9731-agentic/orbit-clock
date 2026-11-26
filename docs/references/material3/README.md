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

- Current time leads at 45sp regular weight (40sp in short windows).
- The smaller snooze-duration number is 18sp, with a quiet unit beneath.
- +/- have contrasting filled targets: 72dp normally and 64dp in short windows. They only adjust the occurrence's duration; Snooze remains a separate tap.
- Snooze/Stop use lower-emphasis tonal actions with 48dp touch targets and 14sp labels.
- The alarm label is last, at 12sp in the on-surface-variant role; its full value remains available to accessibility.
- Rounded dark surfaces, restrained elevation, paired Material color roles and wallpaper-derived dynamic color when Android supports it.
- The window stays mounted during duration changes. Rotation uses current window bounds and system/cutout insets; a height-clamped scrolling viewport keeps controls reachable in short windows or with large text.
- Preserve 5–60 minute bounds, media restoration and persistent-window behavior.
- The official documents include newer Expressive APIs too. This app keeps Material 1.12 and uses compatible components/attributes.

These choices apply the user's requested clock-first hierarchy. They are Orbit-specific decisions, not a claim that Google specifies this exact alarm layout. Native portrait/landscape and enlarged-text checks are recorded in verification/STATUS.md.
