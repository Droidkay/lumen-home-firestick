#!/usr/bin/env bash
# Builds Lumen Home into a signed APK without Gradle.
# Needs: aapt2, dx (or d8), android jars for API 22 (code) and 29 (resources), a JDK.
set -euo pipefail
cd "$(dirname "$0")"

TOOLS=${TOOLS:-/tmp/claude-0}
AAPT2=${AAPT2:-$TOOLS/tool-aapt/package/bin/x64/linux/aapt2}
DX=${DX:-$TOOLS/dx/dex-tools-2.1/lib/dx-27.0.3.jar}
JAR_RES=${JAR_RES:-$TOOLS/sdk/android-29.jar}
JAR_CODE=${JAR_CODE:-$TOOLS/sdk/android-29.jar}
KEYSTORE=keystore/lumen.jks
KEYPASS=${LUMEN_KEYPASS:?set LUMEN_KEYPASS to your keystore password}

rm -rf build && mkdir -p build/gen build/classes

echo "== resources"
"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -o build/base.apk -I "$JAR_RES" --manifest AndroidManifest.xml \
  --java build/gen --min-sdk-version 22 --target-sdk-version 29 \
  --version-code 5 --version-name 1.2.2 build/res.zip

echo "== java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$JAR_CODE" -d build/classes \
  $(find src build/gen -name '*.java')

echo "== dex"
java -jar "$DX" --dex --min-sdk-version=22 --output=build/classes.dex build/classes

echo "== package"
cp build/base.apk build/unsigned.apk
(cd build && zip -q -0 unsigned.apk classes.dex)

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p keystore
  keytool -genkeypair -keystore "$KEYSTORE" -storepass $KEYPASS -keypass $KEYPASS \
    -alias lumen -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Lumen Home, O=DroidKay, C=NG" >/dev/null 2>&1
fi

echo "== sign + align"
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE" \
  -storepass $KEYPASS -keypass $KEYPASS -signedjar build/signed.apk build/unsigned.apk lumen >/dev/null
python3 tools/zipalign.py build/signed.apk build/LumenHome.apk
jarsigner -verify build/LumenHome.apk | tail -1
ls -la build/LumenHome.apk
