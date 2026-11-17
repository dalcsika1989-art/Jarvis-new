#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="$ANDROID_SDK_ROOT"
BT="$SDK/build-tools/35.0.0"
AJ="$SDK/platforms/android-35/android.jar"
for t in aapt2 d8 zipalign apksigner; do test -x "$BT/$t" || { echo "Missing $t"; exit 1; }; done
test -f "$AJ"
mkdir -p build/generated build/classes build/dex build/output
"$BT/aapt2" compile --dir res -o build/resource.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets --java build/generated build/resource.zip
javac -source 8 -target 8 -Xlint:-options -classpath "$AJ" -d build/classes $(find src build/generated -name '*.java' -type f)
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex $(find build/classes -name '*.class' -type f)
test -s build/dex/classes.dex
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ ! -f signing.keystore ]]; then
 keytool -genkeypair -keystore signing.keystore -storepass android -keypass android -alias jarvisC -dname 'CN=JARVIS C Prototype' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
"$BT/apksigner" sign --ks signing.keystore --ks-key-alias jarvisC --ks-pass pass:android --out build/output/JARVIS-C-HUD.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/output/JARVIS-C-HUD.apk
"$BT/aapt2" dump badging build/output/JARVIS-C-HUD.apk | grep -q "package: name='hu.jarvis.c.hud'"
sha256sum build/output/JARVIS-C-HUD.apk
echo "BUILT APK build/output/JARVIS-C-HUD.apk"