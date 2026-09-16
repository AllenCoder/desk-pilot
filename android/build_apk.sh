#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$DIR/.." && pwd)"

# 自动定位 Android SDK
if [ -z "$SDK_DIR" ]; then
    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        SDK_DIR="$ANDROID_HOME"
    elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
        SDK_DIR="$ANDROID_SDK_ROOT"
    elif [ -d "$ROOT_DIR/android-sdk" ]; then
        SDK_DIR="$ROOT_DIR/android-sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        SDK_DIR="$HOME/Library/Android/sdk"
    fi
fi

if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
    echo "Error: Android SDK directory not found. Please set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
    exit 1
fi

BUILD_TOOLS="$(ls -d "$SDK_DIR/build-tools/"* 2>/dev/null | sort -V | tail -n 1)"
PLATFORM="$(ls -d "$SDK_DIR/platforms/android-"* 2>/dev/null | sort -V | tail -n 1)"
APP_DIR="$DIR"
OUT_DIR="$ROOT_DIR/build_out"

# 自动发现并导出 Java 环境
if [ -z "$JAVA_HOME" ]; then
  for JDIR in /usr/local/opt/openjdk /usr/local/Cellar/openjdk/* /Library/Java/JavaVirtualMachines/*/Contents/Home /usr/lib/jvm/*; do
    if [ -d "$JDIR" ]; then
      export JAVA_HOME="$JDIR"
      export PATH="$JDIR/bin:$PATH"
      break
    fi
  done
fi

JAVAC=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVAC="$JAVA_HOME/bin/javac"
elif which javac >/dev/null 2>&1; then
  JAVAC="$(which javac)"
elif [ -d /usr/local/Cellar/openjdk ]; then
  JAVAC=$(ls -d /usr/local/Cellar/openjdk/*/bin/javac 2>/dev/null | head -n 1)
fi

if [ -z "$JAVAC" ]; then
  JAVAC="/usr/local/opt/openjdk/bin/javac"
fi

JAR_BIN=$(dirname "$JAVAC")/jar
KEYTOOL_BIN=$(dirname "$JAVAC")/keytool

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

echo "[3/6] Compiling Java sources via $JAVAC..."
"$JAVAC" -source 1.8 -target 1.8 \
  -bootclasspath "$PLATFORM/android.jar" \
  -cp "$OUT_DIR/gen" \
  -d "$OUT_DIR/obj" \
  "$OUT_DIR/gen/com/antigravity/machud/R.java" \
  "$APP_DIR/src/com/antigravity/machud/"*.java

echo "[4/6] Converting bytecode to Dalvik Executable (classes.dex) via D8..."
R8_JAR="$(find "$SDK_DIR" -name "r8.jar" 2>/dev/null | tail -n 1)"
if [ -n "$R8_JAR" ]; then
  java -cp "$R8_JAR" com.android.tools.r8.D8 \
    "$OUT_DIR/obj/com/antigravity/machud/"*.class \
    --lib "$PLATFORM/android.jar" \
    --output "$OUT_DIR/apk"
elif [ -x "$BUILD_TOOLS/d8" ]; then
  "$BUILD_TOOLS/d8" "$OUT_DIR/obj/com/antigravity/machud/"*.class \
    --lib "$PLATFORM/android.jar" \
    --output "$OUT_DIR/apk"
else
  echo "Error: Neither r8.jar nor d8 found in $SDK_DIR" >&2
  exit 1
fi

cd "$OUT_DIR/apk"
"$JAR_BIN" -uf "$OUT_DIR/unaligned.apk" classes.dex
cd - >/dev/null

echo "[5/6] ZipAligning APK package..."
"$BUILD_TOOLS/zipalign" -f 4 "$OUT_DIR/unaligned.apk" "$OUT_DIR/aligned.apk"

echo "[6/6] Signing APK with permanent release keystore..."
RELEASE_KEYSTORE="$APP_DIR/deskpilot_release.keystore"
if [ ! -f "$RELEASE_KEYSTORE" ]; then
  echo "Generating permanent release keystore..."
  "$KEYTOOL_BIN" -genkeypair -validity 36500 -dname "CN=DeskPilot,OU=Engineering,O=AllenCoder,L=Hangzhou,ST=Zhejiang,C=CN" \
    -keystore "$RELEASE_KEYSTORE" -storepass deskpilot123 -keypass deskpilot123 -alias deskpilot -keyalg RSA -keysize 2048
fi

"$BUILD_TOOLS/apksigner" sign --ks "$RELEASE_KEYSTORE" --ks-pass pass:deskpilot123 --key-pass pass:deskpilot123 \
  --out "$APP_DIR/DeskPilot.apk" "$OUT_DIR/aligned.apk"

cp "$APP_DIR/DeskPilot.apk" "$APP_DIR/MacHUD.apk"

echo "=================================================="
echo "🎉 BUILD SUCCESS! APK generated at:"
echo "   $APP_DIR/DeskPilot.apk"
echo "=================================================="
ls -lh "$APP_DIR/DeskPilot.apk"
