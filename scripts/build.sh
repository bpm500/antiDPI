#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC_DIR="$ROOT_DIR/toolchain"

export JAVA_HOME="$TC_DIR/jdk"
export ANDROID_HOME="$TC_DIR/android-sdk"
export ANDROID_SDK_ROOT="$TC_DIR/android-sdk"
export ANDROID_USER_HOME="$TC_DIR/android-home"
export ANDROID_NDK_HOME="$TC_DIR/android-sdk/ndk/26.3.11579264"
export GRADLE_USER_HOME="$TC_DIR/gradle-home"
mkdir -p "$ANDROID_USER_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "Error: Portable JDK not found. Run scripts/setup.sh first."
    exit 1
fi

if [ ! -f "$ROOT_DIR/antidpi-release.jks" ]; then
    bash "$ROOT_DIR/scripts/make_keystore.sh"
fi

bash "$ROOT_DIR/scripts/build_native.sh"

if [ ! -x "$ROOT_DIR/gradlew" ]; then
    "$TC_DIR/gradle/bin/gradle" --project-dir "$ROOT_DIR" wrapper --gradle-version 8.7
fi

cd "$ROOT_DIR"
./gradlew --no-daemon assembleRelease

mkdir -p "$ROOT_DIR/out"
RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$RELEASE_APK" ]; then
    RELEASE_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
fi

APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
cp -f "$RELEASE_APK" "$ROOT_DIR/out/antiDPI.apk"
"$APKSIGNER" sign --ks "$ROOT_DIR/antidpi-release.jks" --ks-pass pass:antidpi123 --ks-key-alias antidpi --key-pass pass:antidpi123 "$ROOT_DIR/out/antiDPI.apk"
"$APKSIGNER" verify --verbose "$ROOT_DIR/out/antiDPI.apk"

echo "Build succeeded: $ROOT_DIR/out/antiDPI.apk"
