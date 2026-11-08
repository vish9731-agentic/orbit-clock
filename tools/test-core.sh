#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
check_dir="$(mktemp -d)"
trap 'rm -rf "$check_dir"' EXIT
if command -v javac >/dev/null; then
  compiler=(javac)
else
  compiler=(java --module jdk.compiler/com.sun.tools.javac.Main)
fi
"${compiler[@]}" -source 17 -target 17 -Xlint:-options -d "$check_dir" \
  "$project_dir/core/src/main/java/dev/orbit/clock/core/AlarmTiming.java" \
  "$project_dir/core/src/main/java/dev/orbit/clock/core/RotaryGesture.java" \
  "$project_dir/core/src/main/java/dev/orbit/clock/core/SnoozeDuration.java" \
  "$project_dir/core/src/main/java/dev/orbit/clock/core/RingAtmosphere.java" \
  "$project_dir/tools/CoreChecks.java"
java -cp "$check_dir" CoreChecks
"${compiler[@]}" -source 17 -target 17 -Xlint:-options -d "$check_dir" \
  "$project_dir/phone/src/main/java/dev/orbit/clock/phone/WidgetTiming.java" \
  "$project_dir/tools/WidgetChecks.java"
java -cp "$check_dir" WidgetChecks
"${compiler[@]}" -source 17 -target 17 -Xlint:-options -d "$check_dir" \
  "$project_dir/core/src/main/java/dev/orbit/clock/core/AlarmTiming.java" \
  "$project_dir/core/src/main/java/dev/orbit/clock/core/AlarmResumeTiming.java" \
  "$project_dir/tools/ResumeChecks.java"
java -cp "$check_dir" ResumeChecks

