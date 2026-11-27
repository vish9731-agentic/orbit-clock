#!/usr/bin/env bash
set -euo pipefail
theme_root=tools/theme
theme_build=build/theme-probe
theme_tools="$ANDROID_HOME/build-tools/34.0.0"
theme_android="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$theme_build/classes" "$theme_build/dex"
javac -source 8 -target 8 -classpath "$theme_android" -d "$theme_build/classes" "$theme_root/ThemeProbe.java"
mapfile -t theme_classes < <(find "$theme_build/classes" -name '*.class' -print)
"$theme_tools/d8" --min-api 31 --lib "$theme_android" --output "$theme_build/dex" "${theme_classes[@]}"
"$theme_tools/aapt2" link -I "$theme_android" --manifest "$theme_root/probe_manifest.xml" -o "$theme_build/unsigned.apk"
zip -j "$theme_build/unsigned.apk" "$theme_build/dex/classes.dex"
"$theme_tools/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$theme_build/probe.apk" "$theme_build/unsigned.apk"
