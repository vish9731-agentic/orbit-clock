#!/usr/bin/env bash
set -euo pipefail
widget_build=build/widget-probe
widget_tools="$ANDROID_HOME/build-tools/34.0.0"
widget_android="$ANDROID_HOME/platforms/android-35/android.jar"
mkdir -p "$widget_build/classes" "$widget_build/dex"
javac -source 8 -target 8 -classpath "$widget_android" -d "$widget_build/classes" tools/widgets/WidgetProbe.java
mapfile -t widget_classes < <(find "$widget_build/classes" -name '*.class' -print)
"$widget_tools/d8" --min-api 30 --lib "$widget_android" --output "$widget_build/dex" "${widget_classes[@]}"
"$widget_tools/aapt2" link -I "$widget_android" --manifest tools/widgets/probe_manifest.xml -o "$widget_build/unsigned.apk"
zip -j "$widget_build/unsigned.apk" "$widget_build/dex/classes.dex"
"$widget_tools/apksigner" sign --ks debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$widget_build/probe.apk" "$widget_build/unsigned.apk"
