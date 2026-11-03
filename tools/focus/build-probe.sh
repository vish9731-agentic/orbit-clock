#!/usr/bin/env bash
set -euo pipefail
bezel_root=tools/focus
bezel_build=build/focus-probe
bezel_tools="$ANDROID_HOME/build-tools/34.0.0"
bezel_android="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$bezel_build/classes" "$bezel_build/dex"
javac -source 8 -target 8 -classpath "$bezel_android" -d "$bezel_build/classes" "$bezel_root/FocusProbe.java"
mapfile -t bezel_classes < <(find "$bezel_build/classes" -name '*.class' -print)
"$bezel_tools/d8" --min-api 31 --lib "$bezel_android" --output "$bezel_build/dex" "${bezel_classes[@]}"
"$bezel_tools/aapt2" link -I "$bezel_android" --manifest "$bezel_root/probe_manifest.xml" -o "$bezel_build/unsigned.apk"
zip -j "$bezel_build/unsigned.apk" "$bezel_build/dex/classes.dex"
"$bezel_tools/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$bezel_build/probe.apk" "$bezel_build/unsigned.apk"
