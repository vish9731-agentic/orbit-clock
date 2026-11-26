# Ringing controls and playback restoration

When Orbit interrupts local media playback, Snooze/Stop ends the alarm audio and releases focus. After the last overlapping alarm ends, Orbit sends one idempotent Android Play command if playback was active before its Pause command. It does not rely on a stream-activity reading that can lag behind Pause. Media that was paused, with no other audio playing, is not started. Unexpected service destruction does not explicitly send Play. Routing follows Android's current media-session routing; it cannot guarantee the original session if apps change. Apps must support native media controls. Actual YouTube, casting and physical-device Bluetooth behavior require device acceptance.

## Keep controls above other apps

On the phone, open **Review permissions → Keep alarm controls visible**, then enable Android's **Display over other apps** access for Orbit Clock. This is a user-granted permission; installing an APK does not grant it.

With permission, a persistent phone window offers label/time, occurrence-specific snooze duration, Snooze and Stop. Swiping its header, Back in the app beneath it, and outside touches do not dismiss this window. Home can leave the full-screen activity while these controls stay available. The notification remains as a quiet status/control fallback. The window disappears when the occurrence ends, while the full ringing screen is visible, when locking, on permission revocation or service cleanup.

Without permission, Orbit uses the normal Android alarm notification/full-screen path. Android owns heads-up banner swipe/retraction behavior; FLAG_NO_CLEAR and ongoing flags do not override modern SystemUI policy. No ordinary app can make that native banner impossible to swipe away. Protected system screens can hide overlays, and Home, lock, force-stop and system safety controls remain available.

Back does not finish an active RingActivity. The ringing theme opts out of window swipe-to-dismiss. Existing touch/volume/bezel Snooze/Stop handlers remain; volume shortcuts require the ringing activity to be focused. The existing 30-second clock auto-snooze and timer timeout are retained; they also resolve the occurrence, close controls and restore media when applicable. This update blocks accidental UI dismissal, not those configured automatic actions.

The popup puts a large current time first, then the smaller snooze duration between +/- controls, Snooze/Stop, and the quiet alarm label. +/- adjust only; they do not immediately snooze. Rotation rebuilds the layout around the usable window while retaining the occurrence selection. Short windows use compact spacing and a scrolling fallback so the controls remain reachable.

## Verification

The dedicated ringing-session workflow builds a separate same-key native instrumentation APK and independent music/video player. The default UI workflow checks actual alarm delivery, notification PendingIntent actions, overlap/pre-paused guards, playback progress, permitted popup drawing and gesture survival, rendered hierarchy/glyphs, duration actions, permission revocation, cleanup and portrait/landscape rotation at normal/130% text. A separate tile probe verifies per-alarm next-ring data, minute/format/zone updates, lifecycle, rotation and layout. The full-screen Back/Home scope has an unresolved emulator-disconnection gap; its implementation must not be described as runtime verified. These fixtures never ship in Orbit. See HANDOFF.md and verification/STATUS.md for the exact passed source/runs; pending or failed runs are not proof.
