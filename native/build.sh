#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
  if [[ -d "$SDK/ndk" ]]; then
    ANDROID_NDK_HOME="$SDK/ndk/$(ls "$SDK/ndk" | sort -V | tail -1)"
    export ANDROID_NDK_HOME
  fi
fi
echo "Using NDK: ${ANDROID_NDK_HOME:-<none>}"

# ABIS picks what to build (space separated); CI sets only arm64-v8a for ordinary builds to save time.
ABIS="${ABIS:-arm64-v8a armeabi-v7a x86_64}"
ARGS=()
for abi in $ABIS; do
  case "$abi" in
    arm64-v8a)   export CXXFLAGS_aarch64_linux_android="-D_POSIX_C_SOURCE=200809L" ;;
    armeabi-v7a) export CXXFLAGS_armv7_linux_androideabi="-D_POSIX_C_SOURCE=200809L" ;;
    x86_64)      export CXXFLAGS_x86_64_linux_android="-D_POSIX_C_SOURCE=200809L" ;;
    *) echo "Unknown ABI: $abi" >&2; exit 1 ;;
  esac
  ARGS+=(-t "$abi")
done
echo "Building for: $ABIS"

cargo ndk \
  "${ARGS[@]}" \
  -o ../app/src/main/jniLibs \
  build --release

echo "Done. Native libraries:"
find ../app/src/main/jniLibs -name 'libstrata_native.so' -print
