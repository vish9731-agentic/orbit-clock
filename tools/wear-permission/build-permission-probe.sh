#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root after copying these files into tools/wear-permission/.
probe_root=tools/wear-permission
probe_build=build/wear-permission-probe
probe_tools="$ANDROID_HOME/build-tools/34.0.0"
probe_android="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$probe_build/classes" "$probe_build/dex"
javac -source 8 -target 8 -classpath "$probe_android" -d "$probe_build/classes" "$probe_root/AlarmPermissionProbe.java"
"$probe_tools/d8" --lib "$probe_android" --output "$probe_build/dex" "$probe_build/classes/dev/orbit/clock/permissionprobe/AlarmPermissionProbe.class"
"$probe_tools/aapt2" link -I "$probe_android" --manifest "$probe_root/probe_manifest.xml" -o "$probe_build/unsigned.apk"
zip -j "$probe_build/unsigned.apk" "$probe_build/dex/classes.dex"
"$probe_tools/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$probe_build/probe.apk" "$probe_build/unsigned.apk"
