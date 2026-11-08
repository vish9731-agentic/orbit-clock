#!/usr/bin/env bash
set -euo pipefail
resume_root=tools/alarm-resume
resume_build=build/alarm-resume-probe
resume_tools="$ANDROID_HOME/build-tools/34.0.0"
resume_android="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$resume_build/classes" "$resume_build/dex"
javac -source 8 -target 8 -classpath "$resume_android" -d "$resume_build/classes" "$resume_root/ResumeProbe.java"
mapfile -t resume_classes < <(find "$resume_build/classes" -name '*.class' -print)
"$resume_tools/d8" --min-api 31 --lib "$resume_android" --output "$resume_build/dex" "${resume_classes[@]}"
"$resume_tools/aapt2" link -I "$resume_android" --manifest "$resume_root/probe_manifest.xml" -o "$resume_build/unsigned.apk"
zip -j "$resume_build/unsigned.apk" "$resume_build/dex/classes.dex"
"$resume_tools/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$resume_build/probe.apk" "$resume_build/unsigned.apk"
