#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TC_DIR="$ROOT_DIR/toolchain"
JDK_DIR="$TC_DIR/jdk"
SDK_DIR="$TC_DIR/android-sdk"
GRADLE_HOME="$TC_DIR/gradle-home"
DL_DIR="$TC_DIR/downloads"

mkdir -p "$TC_DIR" "$SDK_DIR" "$GRADLE_HOME" "$DL_DIR"

echo "[1/5] Checking portable JDK 17..."
if [ ! -x "$JDK_DIR/bin/java" ]; then
    echo "Downloading Eclipse Temurin JDK 17..."
    curl -fL --retry 3 -o "$DL_DIR/jdk17.tar.gz" "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
    rm -rf "$TC_DIR/jdk_tmp"
    mkdir -p "$TC_DIR/jdk_tmp"
    tar -xzf "$DL_DIR/jdk17.tar.gz" -C "$TC_DIR/jdk_tmp"
    FIRST_DIR="$(find "$TC_DIR/jdk_tmp" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
    mv "$FIRST_DIR" "$JDK_DIR"
    rm -rf "$TC_DIR/jdk_tmp"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

echo "[2/5] Checking Android SDK command-line tools..."
CMDTOOLS_BIN="$SDK_DIR/cmdline-tools/latest/bin"
if [ ! -x "$CMDTOOLS_BIN/sdkmanager" ]; then
    echo "Downloading Android command-line tools..."
    if ! curl -fL --retry 3 -o "$DL_DIR/cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"; then
        curl -fL --retry 3 -o "$DL_DIR/cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip"
    fi
    rm -rf "$TC_DIR/cmd_tmp"
    mkdir -p "$TC_DIR/cmd_tmp"
    unzip -q "$DL_DIR/cmdline-tools.zip" -d "$TC_DIR/cmd_tmp"
    mkdir -p "$SDK_DIR/cmdline-tools"
    rm -rf "$SDK_DIR/cmdline-tools/latest"
    mv "$TC_DIR/cmd_tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
    rm -rf "$TC_DIR/cmd_tmp"
fi

export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$CMDTOOLS_BIN:$SDK_DIR/platform-tools:$PATH"

echo "[3/5] Accepting Android SDK licenses and installing packages..."
yes | "$CMDTOOLS_BIN/sdkmanager" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
"$CMDTOOLS_BIN/sdkmanager" --sdk_root="$SDK_DIR" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.3.11579264" "cmake;3.22.1"

if [ -d "$SDK_DIR/ndk/26.3.11579264" ] && [ ! -e "$TC_DIR/ndk" ]; then
    ln -s "$SDK_DIR/ndk/26.3.11579264" "$TC_DIR/ndk"
fi

echo "[4/5] Checking portable Gradle 8.7..."
if [ ! -x "$TC_DIR/gradle/bin/gradle" ]; then
    curl -fL --retry 3 -o "$DL_DIR/gradle.zip" "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
    rm -rf "$TC_DIR/gradle_tmp"
    mkdir -p "$TC_DIR/gradle_tmp"
    unzip -q "$DL_DIR/gradle.zip" -d "$TC_DIR/gradle_tmp"
    FIRST_DIR="$(find "$TC_DIR/gradle_tmp" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
    mv "$FIRST_DIR" "$TC_DIR/gradle"
    rm -rf "$TC_DIR/gradle_tmp"
fi

echo "[5/5] Setup completed successfully!"
