#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" ]]; then echo "ERROR: Android SDK missing"; exit 1; fi
BT="$SDK/build-tools/35.0.0"
AJ="$SDK/platforms/android-35/android.jar"
for tool in aapt2 d8 zipalign apksigner; do test -x "$BT/$tool" || { echo "ERROR: Missing $BT/$tool"; exit 1; }; done
test -f "$AJ" || { echo "ERROR: Missing $AJ"; exit 1; }
mkdir -p assets build/classes build/dex build/generated
echo "== Compile Android resources =="
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" link -o build/base.apk -I "$AJ" --manifest AndroidManifest.xml -A assets --java build/generated build/resources.zip
echo "== Compile Java =="
mapfile -t sources < <(find src build/generated -type f -name '*.java' | sort)
if [[ ${#sources[@]} -eq 0 ]]; then echo "ERROR: No Java files"; exit 1; fi
javac -source 8 -target 8 -Xlint:-options -classpath "$AJ" -d build/classes "${sources[@]}"
echo "== Convert to DEX =="
mapfile -t classes < <(find build/classes -type f -name '*.class' | sort)
"$BT/d8" --min-api 26 --lib "$AJ" --output build/dex "${classes[@]}"
test -s build/dex/classes.dex || { echo "ERROR: Missing classes.dex"; exit 1; }
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
if [[ ! -d native/build ]]; then echo "ERROR: Offline AI native build missing"; exit 1; fi
if [[ -d native/build ]]; then
  mkdir -p build/apklibs/lib/arm64-v8a
  find native/build -type f -name '*.so' -exec cp -L {} build/apklibs/lib/arm64-v8a/ \;
  test -s build/apklibs/lib/arm64-v8a/libjarvis_llm.so || { echo "Missing native inference library"; exit 1; }
  (cd build/apklibs && zip -q -0 -r ../unsigned.apk lib)
fi
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ ! -f signing.keystore ]]; then
 keytool -genkeypair -keystore signing.keystore -storepass android -keypass android -alias jarvis -dname 'CN=JARVIS Personal Build' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
"$BT/apksigner" sign --ks signing.keystore --ks-pass pass:android --out build/JARVIS-4.0.apk build/aligned.apk
"$BT/apksigner" verify --verbose build/JARVIS-4.0.apk
test -s build/JARVIS-4.0.apk
echo "== Verify essential offline Android native libraries =="
for library in libjarvis_llm.so libllama.so libggml.so libggml-base.so libggml-cpu.so; do
  unzip -Z -1 build/JARVIS-4.0.apk | grep -Fx "lib/arm64-v8a/$library" >/dev/null || {
    echo "ERROR: Missing $library in final APK"; exit 1;
  }
done
"$BT/aapt2" dump badging build/JARVIS-4.0.apk | grep -q "package: name='hu.jarvis.monitor.v22'" || {
  echo "ERROR: APK package mismatch"; exit 1;
}
sha256sum build/JARVIS-4.0.apk
echo "APK BUILT: build/JARVIS-4.0.apk"
