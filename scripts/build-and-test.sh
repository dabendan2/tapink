#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$PROJECT_DIR/android/app"
BUILD_DIR="$APP_DIR/build"
SRC_DIR="$APP_DIR/src/main/kotlin"
TEST_DIR="$APP_DIR/src/test/kotlin"
RES_DIR="$APP_DIR/src/main/res"
MANIFEST="$APP_DIR/src/main/AndroidManifest.xml"
IOS_DIR="$PROJECT_DIR/ios"

DEBUG_KEYSTORE="${DEBUG_KEYSTORE:-/home/ubuntu/tesla_wearable/keys/debug.keystore}"
RELEASE_KEYSTORE="${RELEASE_KEYSTORE:-$PROJECT_DIR/keys/release.jks}"
RELEASE_KEY_ALIAS="${RELEASE_KEY_ALIAS:-ezsign_upload}"
RELEASE_KEY_PASS="${RELEASE_KEY_PASS:-ezsign_release_2026}"

if [ -z "$JAVA_HOME" ] && [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
fi
if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/home/ubuntu/android-sdk}"
ANDROID_JAR="${ANDROID_JAR:-$ANDROID_SDK_ROOT/platforms/android-35/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-$ANDROID_SDK_ROOT/build-tools/35.0.0}"
BUNDLETOOL_JAR="${BUNDLETOOL_JAR:-$ANDROID_SDK_ROOT/bundletool/bundletool.jar}"
KOTLIN_HOME="${KOTLIN_HOME:-/home/ubuntu/kotlinc}"
KOTLINC="${KOTLINC:-$KOTLIN_HOME/bin/kotlinc}"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-$KOTLIN_HOME/lib/kotlin-stdlib.jar}"

# Auto-generate ephemeral debug/release keystores in CI if not present on disk
mkdir -p "$PROJECT_DIR/keys"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
    DEBUG_KEYSTORE="$PROJECT_DIR/keys/ci-debug.keystore"
    if [ ! -f "$DEBUG_KEYSTORE" ]; then
        keytool -genkeypair -v \
            -keystore "$DEBUG_KEYSTORE" \
            -storetype PKCS12 \
            -alias androiddebugkey \
            -keyalg RSA -keysize 2048 -validity 10000 \
            -storepass "android" -keypass "android" \
            -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
    fi
fi

if [ ! -f "$RELEASE_KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$RELEASE_KEYSTORE" \
        -storetype PKCS12 \
        -alias "$RELEASE_KEY_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$RELEASE_KEY_PASS" -keypass "$RELEASE_KEY_PASS" \
        -dname "CN=Dabendan, OU=Mobile, O=Dabendan, L=Taipei, ST=Taiwan, C=TW" >/dev/null 2>&1
fi

echo "=== [1/7] Cleaning & Preparing Build Directories ==="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/test-classes" "$BUILD_DIR/dex" "$BUILD_DIR/gen" "$BUILD_DIR/bundle-proto" "$BUILD_DIR/base-module"

CLASSPATH="$ANDROID_JAR:$KOTLIN_STDLIB"

echo "=== [2/7] Compiling TapInk Android Resources & Kotlin Sources (API 35, -Werror) ==="
"$BUILD_TOOLS/aapt2" compile \
    --dir "$RES_DIR" \
    -o "$BUILD_DIR/res.zip"

"$BUILD_TOOLS/aapt2" link \
    --auto-add-overlay \
    -I "$ANDROID_JAR" \
    --manifest "$MANIFEST" \
    -R "$BUILD_DIR/res.zip" \
    --java "$BUILD_DIR/gen" \
    -o "$BUILD_DIR/unsigned.apk"

"$JAVA_HOME/bin/javac" -cp "$ANDROID_JAR" -d "$BUILD_DIR/classes" "$BUILD_DIR/gen/com/dabendan/tapink/R.java"

"$KOTLINC" -Werror -cp "$CLASSPATH:$BUILD_DIR/classes" \
    -d "$BUILD_DIR/classes" \
    "$SRC_DIR/com/dabendan/tapink/LogManager.kt" \
    "$SRC_DIR/com/dabendan/tapink/TapInkLogService.kt" \
    "$SRC_DIR/com/dabendan/tapink/Lzo1x.kt" \
    "$SRC_DIR/com/dabendan/tapink/FloydSteinbergDitherer.kt" \
    "$SRC_DIR/com/dabendan/tapink/TapInkProtocol.kt" \
    "$SRC_DIR/com/dabendan/tapink/ShareNfcActivity.kt"

echo "=== [3/7] Compiling & Executing Android JVM Unit Tests (-Werror) ==="
"$KOTLINC" -Werror -cp "$CLASSPATH:$BUILD_DIR/classes" \
    -d "$BUILD_DIR/test-classes" \
    "$TEST_DIR/com/dabendan/tapink/TapInkUnitTest.kt"

java -cp "$CLASSPATH:$BUILD_DIR/classes:$BUILD_DIR/test-classes" com.dabendan.tapink.TapInkUnitTest

echo "=== [4/7] Running D8 DEXer & Packaging APK ==="
find "$BUILD_DIR/classes" -name "*.class" > "$BUILD_DIR/class_list.txt"
"$BUILD_TOOLS/d8" --min-api 24 --output "$BUILD_DIR/dex" --lib "$ANDROID_JAR" "$KOTLIN_STDLIB" @"$BUILD_DIR/class_list.txt"

python3 -c "
import zipfile, os
apk_path = '$BUILD_DIR/unsigned.apk'
dex_dir = '$BUILD_DIR/dex'
with zipfile.ZipFile(apk_path, 'a', compression=zipfile.ZIP_DEFLATED) as zf:
    for f in sorted(os.listdir(dex_dir)):
        if f.endswith('.dex'):
            full_path = os.path.join(dex_dir, f)
            zf.write(full_path, f)
"

echo "=== [5/7] Zipalign & Signing APK (Debug + Release) ==="
"$BUILD_TOOLS/zipalign" -p -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"

# 1. Debug-signed APK (preserves existing signature for seamless adb install -r on local devices/emulators)
"$BUILD_TOOLS/apksigner" sign \
    --ks "$DEBUG_KEYSTORE" \
    --ks-pass "pass:android" \
    --out "$BUILD_DIR/tapink.apk" \
    "$BUILD_DIR/aligned.apk"

# 2. Release-signed APK
"$BUILD_TOOLS/apksigner" sign \
    --ks "$RELEASE_KEYSTORE" \
    --ks-key-alias "$RELEASE_KEY_ALIAS" \
    --ks-pass "pass:$RELEASE_KEY_PASS" \
    --key-pass "pass:$RELEASE_KEY_PASS" \
    --out "$BUILD_DIR/tapink-release.apk" \
    "$BUILD_DIR/aligned.apk"

echo "=== [6/7] Packaging & Signing Google Play Store App Bundle (.aab) ==="
"$BUILD_TOOLS/aapt2" link \
    --proto-format \
    --auto-add-overlay \
    -I "$ANDROID_JAR" \
    --manifest "$MANIFEST" \
    -R "$BUILD_DIR/res.zip" \
    -o "$BUILD_DIR/bundle-proto/proto.apk"

python3 -c "
import zipfile, os

proto_apk = '$BUILD_DIR/bundle-proto/proto.apk'
dex_dir = '$BUILD_DIR/dex'
base_zip = '$BUILD_DIR/base-module/base.zip'

with zipfile.ZipFile(proto_apk, 'r') as zin, zipfile.ZipFile(base_zip, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == 'AndroidManifest.xml':
            zout.writestr('manifest/AndroidManifest.xml', data)
        elif item.filename == 'resources.pb':
            zout.writestr('resources.pb', data)
        elif item.filename.startswith('res/'):
            zout.writestr(item.filename, data)
        elif item.filename.startswith('assets/'):
            zout.writestr(item.filename, data)
    for f in sorted(os.listdir(dex_dir)):
        if f.endswith('.dex'):
            full_path = os.path.join(dex_dir, f)
            zout.write(full_path, 'dex/' + f)
"

AAB_UNSIGNED="$BUILD_DIR/tapink-unsigned.aab"
AAB_RELEASE="$BUILD_DIR/tapink-release.aab"
rm -f "$AAB_UNSIGNED" "$AAB_RELEASE"

java -jar "$BUNDLETOOL_JAR" build-bundle \
    --modules="$BUILD_DIR/base-module/base.zip" \
    --output="$AAB_UNSIGNED"

cp "$AAB_UNSIGNED" "$AAB_RELEASE"
jarsigner \
    -keystore "$RELEASE_KEYSTORE" \
    -storepass "$RELEASE_KEY_PASS" \
    -keypass "$RELEASE_KEY_PASS" \
    -sigalg SHA256withRSA \
    -digestalg SHA-256 \
    "$AAB_RELEASE" \
    "$RELEASE_KEY_ALIAS" >/dev/null

java -jar "$BUNDLETOOL_JAR" validate --bundle="$AAB_RELEASE"

echo "=== [7/7] Compiling & Executing iOS TapInkCore Swift Package Unit Tests (swift test) ==="
if command -v swift >/dev/null 2>&1; then
    (cd "$IOS_DIR" && swift test)
else
    echo "[WARN] swift binary not found in PATH; skipping iOS Swift unit tests."
fi

echo "=== Cross-Platform Build, Android JVM + iOS Swift Unit Tests & Play Store AAB Validation Completed Successfully ==="
ls -lh "$BUILD_DIR/tapink.apk" "$BUILD_DIR/tapink-release.apk" "$AAB_RELEASE"

for arg in "$@"; do
    case "$arg" in
        --e2e-emulator)
            echo "=== [E2E] Running Headless Android Emulator E2E Test Suite ==="
            python3 "$PROJECT_DIR/tests/emulator-e2e.test.py"
            ;;
        --install-device)
            echo "=== [DEPLOY] Deploying APK to Physical Android Device via Windows ADB (Install Only) ==="
            WIN_ADB="/mnt/c/Users/BRZ_RTH/platform-tools/adb.exe"
            WIN_TEMP_APK="/mnt/c/Users/BRZ_RTH/AppData/Local/Temp/tapink.apk"
            /usr/bin/adb kill-server 2>/dev/null || true
            "$WIN_ADB" start-server
            DEVICE_SERIAL=$("$WIN_ADB" devices -l | tr -d '\r' | awk '/model:Pixel|55041FDCG0003R/ && $2=="device" {print $1; exit}')
            if [ -z "$DEVICE_SERIAL" ]; then
                DEVICE_SERIAL=$("$WIN_ADB" devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1; exit}')
            fi
            if [ -z "$DEVICE_SERIAL" ]; then
                echo "[ERROR] No physical/mDNS Android device detected by Windows adb.exe!"
                exit 1
            fi
            echo "Detected device: $DEVICE_SERIAL (NOTE: Physical phone is strictly install-only; use --e2e-emulator for testing)"
            cp "$BUILD_DIR/tapink.apk" "$WIN_TEMP_APK"
            "$WIN_ADB" -s "$DEVICE_SERIAL" install -r -g 'C:\Users\BRZ_RTH\AppData\Local\Temp\tapink.apk'
            ;;
    esac
done
