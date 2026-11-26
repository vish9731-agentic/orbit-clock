#!/usr/bin/env bash
set -euo pipefail
scope="${1:-all}"
mkdir -p session-feedback
feedback_pid=
logcat_pid=
cleanup_feedback() {
  if [[ -n "$logcat_pid" ]]; then
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true
    logcat_pid=
  fi
  if [[ -n "$feedback_pid" ]]; then
    kill "$feedback_pid" 2>/dev/null || true
    wait "$feedback_pid" 2>/dev/null || true
    feedback_pid=
  fi
}
trap cleanup_feedback EXIT
run_session() {
  local scale="$1" result=0
  adb logcat -v threadtime AndroidRuntime:V libc:V DEBUG:V ActivityTaskManager:I ActivityManager:I WindowManager:W '*:S' > "session-feedback/live-logcat-$scale.txt" 2>&1 &
  logcat_pid=$!
  # Pull native captures while the guest is alive; a lost emulator must not erase
  # the feedback already produced. This retries reads only, never UI actions.
  (
    while true; do
      timeout 8 adb exec-out run-as dev.orbit.clock tar -C files -cf - session-feedback > "session-feedback/frames-$scale.tmp" 2>/dev/null || true
      if python -c "import tarfile; assert tarfile.is_tarfile('session-feedback/frames-$scale.tmp')" 2>/dev/null; then
        mv "session-feedback/frames-$scale.tmp" "session-feedback/frames-$scale.tar"
      fi
      sleep 4
    done
  ) &
  feedback_pid=$!
  adb shell am instrument -w -e scope "$scope" -e scale "$scale" dev.orbit.clock.sessionprobe/dev.orbit.clock.sessionprobe.SessionProbe > "session-feedback/evidence-$scale.txt" || result=$?
  cleanup_feedback
  timeout 8 adb exec-out run-as dev.orbit.clock tar -C files -cf - session-feedback > "session-feedback/frames-$scale.final" 2>/dev/null || true
  if python -c "import tarfile; assert tarfile.is_tarfile('session-feedback/frames-$scale.final')" 2>/dev/null; then
    mv "session-feedback/frames-$scale.final" "session-feedback/frames-$scale.tar"
  fi
  timeout 8 adb exec-out screencap -p > "session-feedback/display-$scale.png" || true
  timeout 8 adb logcat -d -b crash > "session-feedback/crashes-$scale.txt" || true
  cat "session-feedback/evidence-$scale.txt"
  if [[ "$result" -ne 0 ]]; then return "$result"; fi
  python -c "from pathlib import Path; s=Path('session-feedback/evidence-$scale.txt').read_text(); assert 'RINGING_SESSION_RESULT_PASS' in s and 'RINGING_SESSION_FAILURE' not in s, s"
}
adb logcat -c
run_session 100
adb shell am force-stop dev.orbit.clock
adb shell pm clear dev.orbit.clock
adb shell pm grant dev.orbit.clock android.permission.POST_NOTIFICATIONS
adb shell appops set dev.orbit.clock SCHEDULE_EXACT_ALARM allow
adb shell appops set dev.orbit.clock USE_FULL_SCREEN_INTENT ignore
adb shell settings put system font_scale 1.3
run_session 130
python -c "from pathlib import Path; s='\n'.join(p.read_text() for p in Path('session-feedback').glob('crashes-*.txt')); assert 'FATAL EXCEPTION' not in s and 'Fatal signal' not in s, s"
