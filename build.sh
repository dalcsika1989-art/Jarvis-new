#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to an Android SDK with platform/build-tools 35}"
BT="$ANDROID_SDK_ROOT/build-tools/35.0.0"
AJ="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p build/classes build/dex
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets build/resources.zip
java -jar "${ECJ_JAR:?Set ECJ_JAR to the Eclipse Java compiler jar}" -source 1.8 -target 1.8 -bootclasspath "$AJ:$BT/core-lambda-stubs.jar" -d build/classes src/hu/jarvis/app/*.java
find "$PWD/build/classes" -name '*.class' > build/classes.txt
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex @build/classes.txt
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f signing.keystore ]; then keytool -genkeypair -keystore signing.keystore -storepass android -keypass android -alias jarvis -dname 'CN=JARVIS Personal Build' -keyalg RSA -keysize 2048 -validity 10000; fi
"$BT/apksigner" sign --ks signing.keystore --ks-pass pass:android --out build/JARVIS-2.1.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/JARVIS-2.1.apk
