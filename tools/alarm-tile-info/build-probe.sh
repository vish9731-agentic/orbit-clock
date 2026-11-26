#!/usr/bin/env bash
set -euo pipefail
tile_root=tools/alarm-tile-info
tile_build=build/alarm-tile-info-probe
tile_tools="$ANDROID_HOME/build-tools/34.0.0"
tile_android="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$tile_build/classes" "$tile_build/dex"
javac -source 8 -target 8 -classpath "$tile_android" -d "$tile_build/classes" "$tile_root/TileProbe.java"
mapfile -t tile_classes < <(find "$tile_build/classes" -name '*.class' -print)
"$tile_tools/d8" --min-api 31 --lib "$tile_android" --output "$tile_build/dex" "${tile_classes[@]}"
"$tile_tools/aapt2" link -I "$tile_android" --manifest "$tile_root/probe_manifest.xml" -o "$tile_build/unsigned.apk"
zip -j "$tile_build/unsigned.apk" "$tile_build/dex/classes.dex"
"$tile_tools/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$tile_build/probe.apk" "$tile_build/unsigned.apk"
