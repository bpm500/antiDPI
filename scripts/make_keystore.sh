#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYTOOL="$ROOT_DIR/toolchain/jdk/bin/keytool"
KEYSTORE="$ROOT_DIR/antidpi-release.jks"

if [ ! -x "$KEYTOOL" ]; then
    echo "Error: portable JDK keytool not found at $KEYTOOL. Run scripts/setup.sh first."
    exit 1
fi

if [ -f "$KEYSTORE" ]; then
    echo "Keystore already exists: $KEYSTORE"
    exit 0
fi

"$KEYTOOL" -genkeypair -v -keystore "$KEYSTORE" -alias antidpi -keyalg RSA -keysize 2048 -validity 10000 -storepass antidpi123 -keypass antidpi123 -dname "CN=antiDPI, OU=Mobile, O=antiDPI, L=Local, S=Local, C=US"
echo "Created keystore: $KEYSTORE"
