# POC Camera

Proof of concept for a family-friendly Android application that provides remote
access to a private [Frigate](https://frigate.video/) installation through
embedded Tailscale connectivity, without requiring the official Tailscale
Android application.

See `docs/REQUIREMENTS.md` and `docs/PLAN.md` for the full POC scope, milestones
and decision log.

## Status

Phase 2 — Transparent Frigate Connectivity. The app probes Frigate
(`GET /api/version`) over the home LAN first (2 s timeout) and falls back to
the embedded Tailscale network when the local attempt fails. Phase 1 validated
the embedded tsnet node on a physical Android device via an isolated patched Go
toolchain (netlinkrib fix) plus the `TS_LOGS_DIR` fix; see
`docs/TOOLCHAIN_PATCH.md` and the PLAN.md decision log.

## Prerequisites

- JDK 21
- Android SDK with platform `android-36`, build tools `36.0.0`, and NDK
  `27.2.12479018`
- `ANDROID_HOME` set (for example `/opt/android-sdk`)
- Go toolchain 1.26.5+ (auto-downloaded by the `go.mod` `toolchain` directive)
- `gomobile` from `golang.org/x/mobile/cmd/gomobile` on `PATH`

The Gradle wrapper is committed, so a local Gradle installation is not required.

## Commands

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
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

### netlinkrib experiment (optional)

To build the APK with the isolated patched Go toolchain (Android 11+ stdlib
netlink fallback, see `docs/TOOLCHAIN_PATCH.md`):

```text
./gradlew -PgoToolchainRoot=/workspace/go-patched :app:assembleDebug
```

The normal build never uses the patched toolchain implicitly.

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

To install on a connected physical device:

```text
./gradlew installDebug
```

## On-device test (Phase 2)

1. Install and launch the app.
2. The app probes Frigate over the local network first:
   - **Home Wi-Fi:** the app shows `Connected via LOCAL` and the Frigate
     version; Tailscale stays stopped.
   - **Mobile data / non-home Wi-Fi:** the local probe fails within ~2 s, the
     embedded Tailscale node starts, and the app shows `Connected via TAILSCALE`
     plus the Frigate version.
3. If the embedded node has never been enrolled, the app shows a
   `login.tailscale.com/...` URL. Open it in a browser signed in to the tailnet
   admin account, then tap **Retry**.

The used transport is also visible in logcat under the `FrigateConnection` tag
(`local probe started/succeeded/failed`, `Tailscale fallback started`,
`Frigate probe via Tailscale started/succeeded`, `connection failed`).

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

Phase 0 exit condition requires a blank/minimal APK running on a physical
Android device. This repository verifies `assembleDebug`, `test` and `lint`
in the development environment; the on-device launch test must still be
performed.
