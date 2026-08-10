#!/usr/bin/env bash
# Builds the Android AAR for the embedded Tailscale Go component using
# `gomobile bind`. Output is written to <repo>/native/tailscale/build/tsembed.aar
# and is consumed by the `native:tailscale` Gradle module.
#
# Requirements:
#   - Go toolchain 1.26.5+ (the go.mod `toolchain` directive auto-downloads it)
#   - Android SDK with NDK (ANDROID_HOME set; NDK 27.2.12479018 default)
#   - gomobile (golang.org/x/mobile/cmd/gomobile) on PATH
#
# Usage:
#   ./build-android.sh
#
# The build uses the standard Go toolchain (resolved via go.mod, GOTOOLCHAIN=auto)
# by default. To validate the Phase 1 netlinkrib experiment, point PATCHED_GOROOT
# at an isolated Go toolchain built from source with the CL 507415 Android netlink
# fix applied (see docs/TOOLCHAIN_PATCH.md):
#   PATCHED_GOROOT=/workspace/go-patched ./build-android.sh
# The patched toolchain is used only when PATCHED_GOROOT is set explicitly; the
# normal build never depends on it silently.
set -euo pipefail

cd "$(dirname "$0")"

REPO_ROOT="$(cd ../../.. && pwd)"
export GOPATH="${GOPATH:-$REPO_ROOT/.go}"
export GOMODCACHE="${GOMODCACHE:-$GOPATH/mod}"
export GOCACHE="${GOCACHE:-$GOPATH/build-cache}"
export GOTMPDIR="${GOTMPDIR:-$GOPATH/tmp}"
export PATH="$PATH:$GOPATH/bin"

PATCHED_GOROOT="${PATCHED_GOROOT:-}"
if [[ -n "$PATCHED_GOROOT" ]]; then
  if [[ ! -x "$PATCHED_GOROOT/bin/go" ]]; then
    echo "PATCHED_GOROOT is set but $PATCHED_GOROOT/bin/go was not found" >&2
    exit 1
  fi
  export GOROOT="$PATCHED_GOROOT"
  export GOTOOLCHAIN=local
  export PATH="$PATCHED_GOROOT/bin:$PATH"
  echo "Using patched Go toolchain: $("$PATCHED_GOROOT/bin/go" version) (GOROOT=$GOROOT)"
fi

ANDROID_HOME="${ANDROID_HOME:?ANDROID_HOME must be set (e.g. /opt/android-sdk)}"
NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/$NDK_VERSION}"

if ! command -v gomobile >/dev/null 2>&1; then
  echo "gomobile not found. Run: go install golang.org/x/mobile/cmd/gomobile@latest" >&2
  exit 1
fi

mkdir -p "$GOMODCACHE" "$GOCACHE" "$GOTMPDIR" ../build
out="$(cd ../build && pwd)/tsembed.aar"

gomobile bind \
  -target=android/arm64,android/amd64 \
  -androidapi 26 \
  -javapkg com.homelab.poc \
  -o "$out" \
  .

echo "AAR written to $out"
