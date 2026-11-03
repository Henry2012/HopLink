#!/bin/bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/Users/hqq/Library/Android/sdk
export GOMODCACHE="$TASK_ROOT/.tools/go-mod"
export GOCACHE="$TASK_ROOT/.tools/go-cache"
export GOTMPDIR="$TASK_ROOT/.tools/go-tmp"
export GRADLE_USER_HOME="$TASK_ROOT/.tools/gradle-home"
mkdir -p "$GOMODCACHE" "$GOCACHE" "$GOTMPDIR" "$GRADLE_USER_HOME"
python3 "$TASK_ROOT/scripts/prepare_signing.py"
TASK_NDK="$TASK_ROOT/.tools/android-sdk/ndk/27.2.12479018"
TASK_TOOLCHAIN="$TASK_NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin"
test -x "$TASK_TOOLCHAIN/aarch64-linux-android26-clang"
mkdir -p "$TASK_ROOT/app/src/main/jniLibs/arm64-v8a"
cd "$TASK_ROOT/native"
CGO_ENABLED=1 GOOS=android GOARCH=arm64 \
 CC="$TASK_TOOLCHAIN/aarch64-linux-android26-clang" \
 CGO_LDFLAGS='-Wl,-z,max-page-size=16384' \
 go build -mod=mod -tags with_gvisor -trimpath -buildmode=c-shared -ldflags='-s -w' \
 -o "$TASK_ROOT/app/src/main/jniLibs/arm64-v8a/libsimplenode.so" .
cd "$TASK_ROOT"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
TASK_GRADLE="$(find /Users/hqq/.gradle/wrapper/dists/gradle-8.9-bin -path '*/bin/gradle' -type f | head -1)"
"$TASK_GRADLE" --no-daemon :app:assembleDebug :app:assembleRelease
