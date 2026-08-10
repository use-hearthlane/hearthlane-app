# Isolated Patched Go Toolchain (netlinkrib experiment)

Phase 1 device testing is blocked by a Go standard library issue: on Android
11+ (SDK 30) the SELinux policy for `untrusted_app` denies the netlink socket
operations used by `net.Interfaces()` in `src/net/interface_linux.go`, so
`tsnet.Start()` fails with `tsnet: route ip+net: netlinkrib: permission denied`.

No upstream fix is merged as of Go 1.26.5. This document describes a strictly
**isolated, experimental** workaround: a Go toolchain built from the official
Go 1.26.5 source with an upstream Android netlink fallback patch applied. The
patched toolchain is never committed and never used implicitly by the normal
build.

## Origin of the patch

- Go Gerrit CL 507415: `net: add Android interfaces() fallback using ioctl`
  - https://go-review.googlesource.com/c/go/+/507415
  - Status: NEW, never merged; last touched 2024-03-04.
- Mirror PR: https://github.com/golang/go/pull/61089 (OPEN, never merged).
- Related upstream issue: https://github.com/golang/go/issues/40569 (`net.InterfaceAddrs()`
  fails on Android SDK 30).
- Related Tailscale issue: `tailscale/tailscale#17311` (tsnet on gomobile/Android).

Known upstream concerns: the fix relies on `SIOCGIF*` ioctls that may not fully
reflect newer Android netlink/interface behavior, and one user reported it does
not fully work on Android 14 / targetSdk 34. The POC treats the patch as a
binary experiment, not a production path.

## What the patch changes

- `src/syscall/netlink_linux.go`: `NetlinkRIB` no longer fails hard on
  `EPERM`/`EACCES` for the link/address query used by `net`.
- `src/net/interface_linux.go`: `interfaceTable` falls back to
  `interfaceTableAndroid` (ioctl-based `SIOCGIF*`) when the netlink query is
  denied; `InterfaceAddrs` uses `interfaceTable`. Known limitation: MAC
  addresses are not reported.

The exact diff is versioned at `native/tailscale/go/patches/cl507415-android-netlink.patch`.

## Reproducing the toolchain

The source of the module toolchain `go1.26.5` is fetched automatically into the
Go module cache by `GOTOOLCHAIN=auto`. Steps (Linux amd64, bootstrap Go
installed system-wide):

```bash
# 1. Copy the official toolchain source out of the module cache.
cp -a "$(go env GOMODCACHE)/golang.org/toolchain@v0.0.1-go1.26.5.linux-amd64" \
      /workspace/go-patched
chmod -R u+w /workspace/go-patched

# 2. Apply the isolated patch.
cd /workspace/go-patched
patch -p1 < native/tailscale/go/patches/cl507415-android-netlink.patch
# (use the absolute path when not working from the repo)

# 3. Rebuild the toolchain with a bootstrapping Go distribution.
cd /workspace/go-patched/src
GOROOT_BOOTSTRAP=/usr/local/go ./make.bash

# 4. Verify.
/workspace/go-patched/bin/go version   # go1.26.5 linux/amd64
```

The environment does not need network access for the module download in step 1
when the toolchain is already cached; the build itself is offline.

## Building the AAR/APK with the patched toolchain

The normal build is unchanged and never uses the patched toolchain implicitly.
To build the experiment variant explicitly:

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew -PgoToolchainRoot=/workspace/go-patched :app:assembleDebug
```

or, for the standalone AAR:

```bash
PATCHED_GOROOT=/workspace/go-patched ./native/tailscale/go/build-android.sh
```

`build-android.sh` logs which toolchain it uses (`Using patched Go toolchain: ...`
vs the default), so the experiment build is auditable in the build output.

## Verifying the patch is in the artifact

The patched functions are compiled into `libgojni.so`. After building the APK:

```bash
unzip -o -q app/build/outputs/apk/debug/app-debug.apk 'lib/arm64-v8a/*'
/workspace/go-patched/bin/go tool nm lib/arm64-v8a/libgojni.so | grep interfaceTableAndroid
```

Expected: `net.interfaceTableAndroid` is present, and the Android path no longer
calls `syscall.NetlinkRIB`.

## Constraints

- The patched toolchain directory is large (multi-hundred MB) and is
  git-ignored (`go-patched/`); it must be rebuilt locally per developer.
- This is an experiment for device validation only. Production would use an
  upstream-merged fix or a supported Tailscale code path (see PLAN.md Decision
  Log).
