#!/usr/bin/env bash
set -euo pipefail
session_root=tools/ringing-session
session_build=build/ringing-session-probe
session_tools="$ANDROID_HOME/build-tools/34.0.0"
session_android="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$session_build/classes" "$session_build/dex"
javac -source 8 -target 8 -classpath "$session_android" -d "$session_build/classes" "$session_root/SessionProbe.java"
mapfile -t session_classes < <(find "$session_build/classes" -name '*.class' -print)
"$session_tools/d8" --min-api 31 --lib "$session_android" --output "$session_build/dex" "${session_classes[@]}"
"$session_tools/aapt2" link -I "$session_android" --manifest "$session_root/probe_manifest.xml" -o "$session_build/unsigned.apk"
zip -j "$session_build/unsigned.apk" "$session_build/dex/classes.dex"
"$session_tools/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$session_build/probe.apk" "$session_build/unsigned.apk"
