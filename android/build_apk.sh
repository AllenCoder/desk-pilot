#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$DIR/.." && pwd)"

# 自动定位 Android SDK
SDK_DIR="${ANDROID_SDK_ROOT:-$ROOT_DIR/android-sdk}"
if [ ! -d "$SDK_DIR" ] && [ -d "$HOME/Library/Android/sdk" ]; then
    SDK_DIR="$HOME/Library/Android/sdk"
fi

BUILD_TOOLS="$(ls -d "$SDK_DIR/build-tools/"* 2>/dev/null | tail -n 1)"
PLATFORM="$(ls -d "$SDK_DIR/platforms/android-"* 2>/dev/null | tail -n 1)"
APP_DIR="$DIR"
OUT_DIR="$ROOT_DIR/build_out"

# 自动发现并导出 Java 环境
for JDIR in /usr/local/opt/openjdk /usr/local/Cellar/openjdk/* /Library/Java/JavaVirtualMachines/*/Contents/Home; do
  if [ -d "$JDIR" ]; then
    export JAVA_HOME="$JDIR"
    export PATH="$JDIR/bin:$PATH"
    break
  fi
done

echo "[1/6] Cleaning and preparing output directories..."
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/gen" "$OUT_DIR/obj" "$OUT_DIR/apk"

echo "[2/6] Compiling and linking Android resources via aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$APP_DIR/res" -o "$OUT_DIR/resources.zip"
"$BUILD_TOOLS/aapt2" link "$OUT_DIR/resources.zip" \
  -I "$PLATFORM/android.jar" \
  --manifest "$APP_DIR/AndroidManifest.xml" \
  --java "$OUT_DIR/gen" \
  -o "$OUT_DIR/unaligned.apk"

echo "[3/6] Locating JDK and compiling Java sources..."
JAVAC=$(which javac 2>/dev/null || true)
if [ -z "$JAVAC" ] || ! "$JAVAC" -version >/dev/null 2>&1; then
  JAVAC=$(ls -d /usr/local/Cellar/openjdk/*/bin/javac 2>/dev/null | head -n 1)
fi
if [ -z "$JAVAC" ]; then
  JAVAC="/usr/local/opt/openjdk/bin/javac"
fi

JAR_BIN=$(dirname "$JAVAC")/jar
KEYTOOL_BIN=$(dirname "$JAVAC")/keytool

"$JAVAC" -source 1.8 -target 1.8 \
  -bootclasspath "$PLATFORM/android.jar" \
  -cp "$OUT_DIR/gen" \
  -d "$OUT_DIR/obj" \
  "$OUT_DIR/gen/com/antigravity/machud/R.java" \
  "$APP_DIR/src/com/antigravity/machud/"*.java

echo "[4/6] Converting bytecode to Dalvik Executable (classes.dex) via D8..."
R8_JAR="$(find "$SDK_DIR" -name "r8.jar" 2>/dev/null | head -n 1)"
if [ -z "$R8_JAR" ] && [ -x "$BUILD_TOOLS/d8" ]; then
  "$BUILD_TOOLS/d8" "$OUT_DIR/obj/com/antigravity/machud/"*.class \
    --lib "$PLATFORM/android.jar" \
    --output "$OUT_DIR/apk"
else
  java -cp "$R8_JAR" com.android.tools.r8.D8 \
    "$OUT_DIR/obj/com/antigravity/machud/"*.class \
    --lib "$PLATFORM/android.jar" \
    --output "$OUT_DIR/apk"
fi

cd "$OUT_DIR/apk"
"$JAR_BIN" -uf "$OUT_DIR/unaligned.apk" classes.dex
cd - >/dev/null

echo "[5/6] ZipAligning APK package..."
"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/unaligned.apk" "$OUT_DIR/aligned.apk"

echo "[6/6] Signing APK with debug keystore..."
DEBUG_KEYSTORE="$APP_DIR/debug.keystore"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
  "$KEYTOOL_BIN" -genkeypair -validity 10000 -dname "CN=MacHUD,O=Antigravity,C=US" \
    -keystore "$DEBUG_KEYSTORE" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048
fi

"$BUILD_TOOLS/apksigner" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$APP_DIR/DeskPilot.apk" "$OUT_DIR/aligned.apk"

cp "$APP_DIR/DeskPilot.apk" "$APP_DIR/MacHUD.apk"

echo "=================================================="
echo "🎉 BUILD SUCCESS! APK generated at:"
echo "   $APP_DIR/DeskPilot.apk"
echo "=================================================="
ls -lh "$APP_DIR/DeskPilot.apk"
