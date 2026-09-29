#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

: "${ANDROID_HOME:=$HOME/Android/Sdk}"
NGROK_VERSION="1.1.1"
mkdir -p vendor

if [ ! -d vendor/ngrok-java ]; then
  git clone --depth 1 --branch v${NGROK_VERSION} https://github.com/ngrok/ngrok-java.git vendor/ngrok-java
fi

NDK_ROOT="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)"
if [ -z "$NDK_ROOT" ]; then
  echo "Android NDK not found. Install an NDK from Android Studio > SDK Manager > SDK Tools."
  exit 1
fi

if ! command -v cargo >/dev/null 2>&1; then echo "Rust/cargo is required."; exit 1; fi
if ! command -v mvn >/dev/null 2>&1; then echo "Maven is required."; exit 1; fi

HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
HOST_ARCH="$(uname -m)"
[ "$HOST_ARCH" = "aarch64" ] && HOST_ARCH="x86_64"
NDK_BIN="$NDK_ROOT/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}/bin"

cd vendor/ngrok-java
mvn compile -pl ngrok-java-native --also-make -q
cd ngrok-java-native/target/classes
jar cf ../ngrok-java-native-classes.jar com/
cd ../../../..

cd vendor/ngrok-java/ngrok-java-native
CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android21-clang" \
AR_aarch64_linux_android="$NDK_BIN/llvm-ar" \
CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android21-clang" \
cargo build --release --target aarch64-linux-android

CC_x86_64_linux_android="$NDK_BIN/x86_64-linux-android21-clang" \
AR_x86_64_linux_android="$NDK_BIN/llvm-ar" \
CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$NDK_BIN/x86_64-linux-android21-clang" \
cargo build --release --target x86_64-linux-android

cd "$ROOT"
cp vendor/ngrok-java/ngrok-java-native/target/ngrok-java-native-classes.jar app/libs/
mkdir -p app/src/main/jniLibs/arm64-v8a app/src/main/jniLibs/x86_64
cp vendor/ngrok-java/ngrok-java-native/target/aarch64-linux-android/release/libngrok_java.so app/src/main/jniLibs/arm64-v8a/
cp vendor/ngrok-java/ngrok-java-native/target/x86_64-linux-android/release/libngrok_java.so app/src/main/jniLibs/x86_64/

echo "ngrok Android native runtime prepared."
