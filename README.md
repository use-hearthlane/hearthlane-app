# Family Camera

Family-friendly Android application that provides remote access to a private
[Frigate](https://frigate.video/) installation through embedded Tailscale
connectivity, without requiring the official Tailscale Android application.

See `docs/REQUIREMENTS.md`, `docs/PLAN.md`, and `docs/V1.md` for the technical
history and scope. See `docs/RELEASE.md` for Play Store build and signing
instructions.

## Status

V1 feature-complete. The app probes Frigate over the home LAN first and falls
back to the embedded Tailscale network when the local attempt fails, then plays
one go2rtc HLS/fMP4 camera stream with ExoPlayer over the chosen transport. The
V1 validation passed on a physical Android device for remote connectivity,
stream discovery, live playback, lock/unlock resume and network-switch reconnect
(see the `docs/PLAN.md` decision log). The project is now in release
engineering / Play Store readiness.

## Prerequisites

- JDK 21
- Android SDK with platform `android-36`, build tools `36.0.0`, and NDK
  `27.2.12479018`
- `ANDROID_HOME` set (for example `/opt/android-sdk`)
- Go toolchain 1.26.5+ (auto-downloaded by the `go.mod` `toolchain` directive)
- `gomobile` from `golang.org/x/mobile/cmd/gomobile` on `PATH`

The Gradle wrapper is committed, so a local Gradle installation is not required.

## Commands

Development and validation:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Play Store release AAB (requires signing credentials; see `docs/RELEASE.md`):

```text
./gradlew :app:bundleRelease
```

Local release APK (requires signing credentials; see `docs/RELEASE.md`):

```text
./gradlew :app:assembleRelease
```

`assembleDebug` builds the embedded Tailscale AAR first (Go + gomobile) and then
the APK. The AAR can also be built standalone:

```text
./native/tailscale/go/build-android.sh
```

This script sets up the Go caches under `<repo>/.go/`, requires `ANDROID_HOME`,
and writes the AAR to `native/tailscale/build/tsembed.aar`. The AAR contains the
gomobile-generated `com.homelab.poc.tsembed.Tsembed` binding (start/stop/status)
and `libgojni.so` for `arm64-v8a` and `x86_64`.

Go checks for the native module:

```text
cd native/tailscale/go
go test ./...
go vet ./...
```

### netlinkrib fix (Android 11+, applied by default when available)

`tsnet.Start()` needs the CL 507415 stdlib fallback on Android 11+ (the Go
stdlib netlink path is denied by SELinux; see `docs/TOOLCHAIN_PATCH.md`). The
repo's isolated patched Go toolchain at `go-patched/` is used automatically when
present, so the standard `./gradlew assembleDebug` produces a device-working
APK. To point at a different patched toolchain:

```text
./gradlew -PgoToolchainRoot=/path/to/toolchain :app:assembleDebug
```

Without a patched toolchain the build falls back to the standard Go toolchain:
compilation succeeds, but `tsnet.Start()` fails on a physical Android 11+
device with `netlinkrib: permission denied`.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. The
release APK is produced at `app/build/outputs/apk/release/app-release.apk` and
the Play Store AAB at `app/build/outputs/bundle/release/app-release.aab`.

To install the debug build on a connected physical device:

```text
./gradlew installDebug
```

## On-device test (V1)

1. Install and launch the app.
2. Complete the one-time administrator setup if this is the first install:
   enter the Frigate URL and test the connection.
3. The app probes Frigate over the local network first:
   - **Home Wi-Fi:** the app connects locally; Tailscale stays stopped.
   - **Mobile data / non-home Wi-Fi:** the local probe fails within ~2 s, the
     embedded Tailscale node starts, and the app connects through Tailscale.
4. If the embedded node has never been enrolled, the app shows a
   `login.tailscale.com/...` URL. Open it in a browser signed in to the tailnet
   admin account, then tap **Retry**.

The Frigate URL is editable in the app before connecting and defaults to the
build-time value (also overridable at build time):

```text
./gradlew -Pfrigate.baseUrl=http://site.omni.corp :app:assembleDebug
```

The same URL is used for both paths. On the Tailscale path the hostname
resolves through the tailnet DNS (homelab DNS configured in the Tailscale admin
console / MagicDNS). On the home-LAN path it resolves through the network the
phone is on; if that DNS does not answer, the app transparently falls back to
the Tailscale path.

No auth key, OAuth secret or other long-lived credential is embedded in the APK.

## Regenerating the Gradle wrapper

The wrapper was generated from the Gradle 8.14.3 distribution. To regenerate it
(requires a local Gradle installation):

```text
gradle wrapper --gradle-version 8.14.3 --distribution-type bin
```

## Module layout

```text
app/                      Android application (Kotlin + Jetpack Compose)
  ui/                     Compose screens
  navigation/             (future) screen navigation

core/connectivity/        Application-oriented connectivity state
core/frigate/             Frigate HTTP integration
core/playback/            Android playback implementation
native/tailscale/         Go/Tailscale integration and Android bridge
  go/                     Go module (tsnet wrapper + gomobile build script)
```

`app` depends on the three `core` modules and on `native/tailscale`, which hosts
the Phase 1 Go bridge.

## Version selection

The following versions were chosen at bootstrap time because they are the
current stable, mutually compatible options for the installed SDK platform
(`android-36`) and JDK 21:

- Gradle wrapper 8.14.3
- Android Gradle Plugin 8.13.2
- Kotlin 2.2.21
- Jetpack Compose BOM 2026.06.01
- `compileSdk` / `targetSdk` 36, `minSdk` 26

## Validation status

V1 was validated on a physical Android device for LOCAL and TAILSCALE
connectivity, live playback, network-switch recovery, lock/unlock resume, and
Tailscale enrollment. The standard development gates are `test`, `lint`,
`:app:assembleDebug`, and `:app:bundleRelease` (the latter requires signing
credentials).
