#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/../.." && pwd)"
probe_dir="$project_dir/tools/media-focus"
probe_work="$project_dir/media-probe-build"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Android SDK required}}"
build_tools="$sdk_dir/build-tools/34.0.0"
mkdir -p "$probe_work/classes" "$probe_work/dex" "$probe_work/gen" "$probe_work/assets"
python3 "$probe_dir/generate-assets.py" "$probe_work/assets"
"$build_tools/aapt2" link -I "$sdk_dir/platforms/android-35/android.jar" --manifest "$probe_dir/probe_manifest.xml" -A "$probe_work/assets" --java "$probe_work/gen" -o "$probe_work/unsigned.apk"
javac -source 17 -target 17 -classpath "$sdk_dir/platforms/android-35/android.jar" -d "$probe_work/classes" "$probe_dir/ProbeController.java" "$probe_dir/MusicService.java" "$probe_dir/ProbeActivity.java"
mapfile -t probe_classes < <(find "$probe_work/classes" -name '*.class')
"$build_tools/d8" --min-api 30 --lib "$sdk_dir/platforms/android-35/android.jar" --output "$probe_work/dex" "${probe_classes[@]}"
zip -j "$probe_work/unsigned.apk" "$probe_work/dex/classes.dex"
"$build_tools/zipalign" -f 4 "$probe_work/unsigned.apk" "$probe_work/aligned.apk"
"$build_tools/apksigner" sign --ks "$project_dir/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$probe_work/media-focus-probe.apk" "$probe_work/aligned.apk"
