# Smart alarm features

Implemented: turn a recurring alarm off for its next occurrence, then choose an explicit date to resume the original repeat pattern. Tuesday night on a Monday–Friday alarm offers skipping Wednesday and resuming Thursday. Friday skips to Monday; weekend-only and custom repeats preserve their days. Daily alarms offer an after-weekend choice when relevant. One-shot alarms offer deduplicated next-day/weekday/weekend dates and remain one-shot. Keep off, Back and dismiss leave the alarm off. Selecting a date immediately schedules it; no delayed re-enable task is needed. Edit/deletion/version checks protect against stale choices. A temporary pause shows its resume date. Bedtime widgets ignore skipped wake dates.

## Further ideas, not enabled

| Idea | Proposed behavior |
| --- | --- |
| Vacation pause | Choose a return date for selected alarms, then resume their existing repeat patterns. |
| Holiday suggestions | With a selected region or calendar, offer a one-off skip before a holiday; require your choice. |
| Alarm groups | Group Work, Runs and Weekends, with pause/resume for a whole group. |
| One-off time change | Move only tomorrow’s alarm later or earlier, preserving later days. |
| Ready-for-morning check | Show whether exact alarms, notifications, volume and paired-watch connectivity are ready. |
| Forgotten-off reminder | At a chosen evening time, offer to restore a weekday alarm you left off; never silently enable it. |
| Calendar-aware suggestions | With permission, suggest an earlier alarm for a specific early appointment, showing the proposed time before applying it. |
| Snooze pattern suggestions | After repeated snoozing, offer a better default duration or wake time; change nothing without selection. |
| Travel mode | On a time-zone change, offer local-time or home-time behavior and preview the next ring before applying it. |

Suggestions must name the exact affected date/time, preserve saved repeats, and always offer Keep off/cancel. A weekday pattern suggests a routine; it does not establish employment, health or holiday plans. Do not infer a reason or alter schedules automatically.
