# Orbit Clock build consolidation

Audit date: 2026-11-08. GitHub repository commits, branches, PRs and Actions were inspected. Other ChatGPT conversation transcripts are not accessible in this session; this inventory covers their committed repository work, not requirements that were never committed or shared here.

| Version/source | Changes and relationship |
| --- | --- |
| 0.1.113 / `32facd7` | Prior alarms, exact-alarm watch fix, media pause, watch bezel/ringing iterations and glance-first square/wide widgets with reversible controls and wallpaper contrast settings. |
| 0.1.122 / `412712e` | Descendant of 0.1.113; includes Pomodoro Focus mode and the subsequent widget contour/contrast fixes from `ddd596d`. PR #4 merged the Focus branch into main. |
| Newer main / `f97c049`, `845cf2d` | Adds temporary alarm-off choices, skipping the next occurrence while retaining saved repeat weekdays, revision-safe restoration, updated widget toggles and native/calendar scenarios. These changes were made by another active session after 0.1.122. |
| Consolidation fix | Builds on newer main and adds explicit phone-to-watch Focus screen handoff, acknowledgment/status/retry, protected one-use watch launch routing, and version-specific APK release links. |

PRs #1 (watch precise alarm access), #2 (bezel aura), #3 (watch spring slider) and #4 (Pomodoro) are merged. Their remote branches contain no commits missing from main at audit time. Superseded visual/control designs are not reintroduced by blindly merging older snapshots. Current HANDOFF.md and INTERACTIONS/MOTION documentation retain the latest chosen behavior.

Build numbers are GitHub Actions run numbers, not counts of distinct features. Failed and PR builds consume numbers too. `debug-latest` is a moving link; an older chat may label that link 0.1.113 even after the assets have advanced. Consolidated main-branch publications also create `debug-0.1.<build>` releases with matching phone/watch APKs and a source commit, so a shared version-specific link is stable.

The final delivered version, source and validation outcomes are recorded in HANDOFF.md and verification/STATUS.md after checks finish. Physical paired-device handoff still requires acceptance on the actual phone/watch; standalone emulators cannot prove Bluetooth delivery.
