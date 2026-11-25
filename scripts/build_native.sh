#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="$ROOT_DIR/toolchain/android-sdk"
NDK_DIR="$SDK_DIR/ndk/26.3.11579264"
NDK_BUILD="$NDK_DIR/ndk-build"
JNILIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"
JOBS="$(nproc 2>/dev/null || echo 4)"

if [ ! -x "$NDK_BUILD" ]; then
    echo "Error: ndk-build not found at $NDK_BUILD. Run scripts/setup.sh first."
    exit 1
fi

echo "Building ciadpi (byedpi)..."
BYEDPI_DIR="$ROOT_DIR/third_party/byedpi"
"$NDK_BUILD" -j"$JOBS" \
    NDK_PROJECT_PATH="$BYEDPI_DIR" \
    APP_BUILD_SCRIPT="$BYEDPI_DIR/Android.mk" \
    NDK_APPLICATION_MK="$BYEDPI_DIR/Application.mk" \
    NDK_OUT="$ROOT_DIR/toolchain/ndk_out/byedpi" \
    NDK_LIBS_OUT="$ROOT_DIR/toolchain/ndk_libs/byedpi" \
    APP_ABI="arm64-v8a armeabi-v7a x86_64" \
    APP_PLATFORM=android-26

echo "Building hev-socks5-tunnel..."
HEV_DIR="$ROOT_DIR/third_party/hev-socks5-tunnel"
"$NDK_BUILD" -j"$JOBS" \
    NDK_PROJECT_PATH="$HEV_DIR" \
    APP_BUILD_SCRIPT="$HEV_DIR/Android.mk" \
    NDK_APPLICATION_MK="$HEV_DIR/Application.mk" \
    NDK_OUT="$ROOT_DIR/toolchain/ndk_out/hev" \
    NDK_LIBS_OUT="$ROOT_DIR/toolchain/ndk_libs/hev" \
    APP_ABI="arm64-v8a armeabi-v7a x86_64" \
    APP_PLATFORM=android-26

for abi in arm64-v8a armeabi-v7a x86_64; do
    mkdir -p "$JNILIBS_DIR/$abi"
    cp -f "$ROOT_DIR/toolchain/ndk_libs/byedpi/$abi/ciadpi" "$JNILIBS_DIR/$abi/libciadpi.so"
    cp -f "$ROOT_DIR/toolchain/ndk_libs/hev/$abi/libhev-socks5-tunnel.so" "$JNILIBS_DIR/$abi/libhev-socks5-tunnel.so"
done

echo "Native binaries copied to app/src/main/jniLibs/"
