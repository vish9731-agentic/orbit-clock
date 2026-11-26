#!/usr/bin/env bash
set -euo pipefail
mkdir -p tile-feedback
run_tiles() {
  local tile_scale="$1" tile_result=0
  adb shell am force-stop dev.orbit.clock
  adb shell pm clear dev.orbit.clock
  adb shell pm grant dev.orbit.clock android.permission.POST_NOTIFICATIONS
  adb shell appops set dev.orbit.clock SCHEDULE_EXACT_ALARM allow
  adb shell appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow
  adb shell appops set dev.orbit.clock USE_FULL_SCREEN_INTENT allow
  adb shell settings put system time_12_24 24
  adb shell settings put system font_scale "$2"
  adb logcat -c
  timeout 240 adb shell am instrument -w -e scale "$tile_scale" dev.orbit.clock.tileprobe/dev.orbit.clock.tileprobe.TileProbe > "tile-feedback/evidence-$tile_scale.txt" || tile_result=$?
  timeout 8 adb exec-out run-as dev.orbit.clock tar -C files -cf - tile-feedback > "tile-feedback/frames-$tile_scale.tar" || true
  timeout 8 adb logcat -d -b crash > "tile-feedback/crashes-$tile_scale.txt" || true
  cat "tile-feedback/evidence-$tile_scale.txt"
  if [[ "$tile_result" -ne 0 ]]; then return "$tile_result"; fi
  python -c "from pathlib import Path; s=Path('tile-feedback/evidence-$tile_scale.txt').read_text(); assert 'ALARM_TILE_INFO_RESULT_PASS' in s and 'ALARM_TILE_INFO_FAILURE' not in s, s"
}
run_tiles 100 1.0
run_tiles 130 1.3
python -c "from pathlib import Path; s='\n'.join(p.read_text() for p in Path('tile-feedback').glob('crashes-*.txt')); assert 'FATAL EXCEPTION' not in s and 'Fatal signal' not in s, s"
