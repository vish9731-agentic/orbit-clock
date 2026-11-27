#!/usr/bin/env bash
set -euo pipefail
theme_module="${1:-phone}"
theme_scale="${2:-1.0}"
mkdir -p theme-results
collect_theme_feedback() {
  timeout 12 adb exec-out run-as dev.orbit.clock tar -C files -cf - theme-feedback > theme-results/frames.tar 2>/dev/null || true
  timeout 10 adb logcat -d -b crash > theme-results/crashes.txt || true
  timeout 10 adb logcat -d -s AndroidRuntime > theme-results/runtime.txt || true
}
trap collect_theme_feedback EXIT
adb logcat -c
if [[ "$theme_module" == widgets ]]; then
  adb shell cmd uimode night yes
  adb shell appwidget grantbind --package dev.orbit.clock --user 0
  timeout 480 adb shell am instrument -w dev.orbit.clock.widgetprobe/dev.orbit.clock.widgetprobe.WidgetProbe > theme-results/widget-checks.txt
  adb pull /sdcard/Android/data/dev.orbit.clock/files/widget-evidence theme-results/widget-frames
  cat theme-results/widget-checks.txt
  python -c "from pathlib import Path; s=Path('theme-results/widget-checks.txt').read_text(); assert 'WIDGET_RESULT_PASS' in s and 'WIDGET_FAILURE' not in s,s"
else
  for theme_night in no yes; do
    adb shell cmd uimode night "$theme_night"
    adb shell am force-stop dev.orbit.clock
    adb shell settings put system font_scale "$theme_scale"
    adb shell cmd uimode night > "theme-results/system-mode-$theme_night.txt"
    timeout 360 adb shell am instrument -w -e module "$theme_module" -e night "$theme_night" -e scale "$theme_scale" dev.orbit.clock.themeprobe/dev.orbit.clock.themeprobe.ThemeProbe > "theme-results/checks-$theme_night.txt"
    cat "theme-results/checks-$theme_night.txt"
    python -c "from pathlib import Path; s=Path('theme-results/checks-$theme_night.txt').read_text(); assert 'THEME_RESULT_PASS' in s and 'THEME_FAILURE' not in s,s"
  done
fi
collect_theme_feedback
python -c "from pathlib import Path; s=Path('theme-results/crashes.txt').read_text(); assert 'FATAL EXCEPTION' not in s and 'Fatal signal' not in s,s"
