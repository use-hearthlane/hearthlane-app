<p align="center">
  <img src="docs/assets/branding/hearthlane-logo.png" alt="Hearthlane" width="320" />
</p>

<h1 align="center">Hearthlane</h1>

<p align="center"><strong>Your private way home.</strong></p>

<p align="center">
  Open-source Android gateway &middot; private homelab access &middot; no public exposure
</p>

---

## Overview

Hearthlane is an **open-source Android gateway** for privately accessing the
services running in your homelab, **without exposing those services to the
public Internet**.

Hearthlane is **self-hosted oriented**, **Android native**, and
**private-by-design**: your cameras, media servers and home automation stay
behind your home network, and Hearthlane reaches them over a private overlay
network you already control. You do not need to publish any port, expose any
reverse proxy, or hand over accounts to a third party.

The first implemented integration is **remote live access to cameras managed by
a private [Frigate](https://frigate.video/) installation**. Hearthlane is
designed so further homelab services can be added behind the same private
gateway later. Those future integrations are architectural direction, not
committed features.

---

## Current capabilities

Hearthlane V1 (Android, versionName `1.0.0` / versionCode `1`) ships the
following end-to-end flow, validated on a physical Android device:

- **Private connectivity** for a single device, using an embedded Tailscale
  node — the official Tailscale Android application is not required.
- **Transparent transport** — the local home LAN is preferred when reachable;
  the embedded Tailscale node is started automatically as a fallback when the
  phone is outside the home LAN, and stopped again as soon as LAN returns.
- **Frigate integration** — camera discovery via `/api/config`, go2rtc stream
  metadata via `/api/go2rtc/streams`, snapshot thumbnails and a single live
  HLS/fMP4 stream per camera through the Frigate `/api/go2rtc/` proxy.
- **Family-facing Android UI** built with Jetpack Compose — Home (camera grid),
  Live View, Diagnostics and Settings screens. Infrastructure terms
  (`LOCAL`/`TAILSCALE`, hostnames, transport labels) are confined to the
  administrator-facing Diagnostics screen.
- **First-run administrator setup** — a single one-time flow enters the Frigate
  URL, tests the connection and walks the administrator through any required
  Tailscale enrollment.
- **Administrator reset path** — Settings can clear the embedded Tailscale
  identity and re-enroll the device without reinstalling.
- **Sanitized diagnostics** — the "Copy diagnostics" action produces a
  plain-text report with sensitive substrings (enrollment URLs, token-shaped
  strings) redacted before anything reaches the clipboard.

---

## Architecture

Hearthlane is organised as a multi-module Android project plus a small Go
component, built into the APK through `gomobile bind`:

```text
Hearthlane (Android, Kotlin, Jetpack Compose)
  |
  +-- app/                     Compose UI, navigation, settings, controllers
  |
  +-- core/
  |     +-- connectivity/      Application-oriented connectivity state
  |     +-- frigate/           Frigate HTTP integration
  |     |     +-- Camera discovery
  |     |     +-- Snapshot thumbnails
  |     |     +-- Live HLS playback metadata
  |     +-- playback/          Android playback (Media3 / ExoPlayer)
  |
  +-- native/
        +-- tailscale/         Go (tsnet) component and Android bridge
              +-- Embedded Tailscale node (tsnet)
              +-- Application-scoped dialer
              +-- Internal DNS resolver
```

The proven module boundaries (`app`, `core/*`, `native/*`) are documented in
[`AGENTS.md`](AGENTS.md) and inherited from the technical validation recorded
in [`docs/PLAN.md`](docs/PLAN.md).

---

## Privacy model

- Homelab services **do not need to be exposed to the public Internet** —
  Hearthlane reaches them over your existing tailnet instead.
- Remote connectivity uses an **embedded Tailscale node** scoped to the app;
  the official Tailscale Android application is not required for this
  application to work, and the connection is owned by the app process.
- The **home LAN is preferred** when reachable; Tailscale is the fallback
  used only when LAN probe fails. Connectivity is automatically restored to
  LAN once it returns.
- Credentials and Tailscale enrollment state **stay on the device** in the
  app's private storage. No reusable Tailscale auth key, OAuth secret,
  Frigate administrator password or camera RTSP credential is embedded in the
  APK or shipped in source control.
- **App backup is disabled**: `android:allowBackup="false"` together with
  explicit `fullBackupContent` and `dataExtractionRules` rules keeps the
  Tailscale state and the app configuration out of cloud backup and device
  transfer.
- The Diagnostics "Copy diagnostics" action **sanitizes sensitive substrings**
  (enrollment URLs, token-shaped runs) before any text reaches the clipboard.
  Release builds also redact HLS session URLs from logs and exception
  messages.

These are the privacy properties currently implemented in the repository.
They are intentional design choices, not absolute guarantees: security
ultimately depends on the Tailscale tailnet policy applied to the family
device identity, the configuration of the homelab services, and the physical
trust placed in the device itself.

---

## Requirements

- An Android phone with **modern Android** (V1 builds against `minSdk = 26`).
- A **homelab Frigate installation** reachable from the LAN during setup and
  reachable through the existing tailnet when the phone is away. The Frigate
  server must speak the public Frigate HTTP API and expose go2rtc under the
  documented `/api/go2rtc/` prefix.
- An **existing Tailscale tailnet** that the homelab belongs to. No Tailscale
  application needs to be installed on the phone; the embedded node enrolls
  interactively the first time it is required.
- A **real device** for the final end-to-end validation. The transparent
  connectivity and the live HLS path depend on application-scoped networking
  and are not equivalent in an emulator.

---

## Building

Prerequisites:

- JDK 21
- Android SDK with platform `android-36`, build tools `36.0.0` and NDK
  `27.2.12479018`
- `ANDROID_HOME` set
- Go toolchain 1.26.5+ (auto-downloaded by the `go.mod` `toolchain` directive)
- `gomobile` from `golang.org/x/mobile/cmd/gomobile` on `PATH`

Standard development gates:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

The Gradle wrapper is committed, so a local Gradle installation is not
required. The `assembleDebug` step runs `gomobile bind` automatically and
produces the AAR wrapped into the APK.

The patched Go toolchain at `go-patched/` is used automatically when present
(the netlink fallback required for `tsnet.Start()` on Android 11+). Without
it the build still succeeds but the embedded node cannot open sockets on a
physical Android 11+ device. See [`docs/TOOLCHAIN_PATCH.md`](docs/TOOLCHAIN_PATCH.md).

Play Store AAB build and signing procedure:

```text
./gradlew :app:bundleRelease
./gradlew :app:assembleRelease
```

Both require external signing credentials. The procedure and the placeholder
upload keystore name are documented in [`docs/RELEASE.md`](docs/RELEASE.md).

Go-side checks (when working in the native module):

```text
cd native/tailscale/go
go test ./...
go vet ./...
```

---

## Screenshots

<!-- Screenshots will be added before the first public release. -->

Real screenshots are captured from a physical device running the current
release build. They will be added under `docs/assets/screenshots/` once the
assets are ready.

---

## Current status

- **Version:** V1, `versionName 1.0.0`, `versionCode 1`.
- **Application ID:** `com.homelab.hearthlane` (Android namespace `com.homelab.poc`
  kept internally to avoid a large source refactor; not user-visible).
- **Validated on a physical Android device** for: home LAN and remote
  playback; stream discovery; lock/unlock resume; Tailscale enrollment;
  network-switch reconnect; transparent LOCAL↔TAILSCALE switch.
- **Not yet published on the Play Store.** Play Store build and signing are
  prepared (see [`docs/RELEASE.md`](docs/RELEASE.md)), but the first listing
  has not been created.

---

## Roadmap

The architecture is designed to grow the gateway beyond the first integration.
The list below is **directional, not a commitment**:

- Bring additional homelab services behind the same private gateway (for
  example media, photos, files, home automation, monitoring).
- Continue to keep the embedded Tailscale node application-scoped; a device-wide
  VPN is not part of the architecture.
- Continue to prefer Frigate/go2rtc as the camera gateway rather than
  reaching cameras directly.
- Each new service integrates through a small focused module boundary
  (`core/<service>/`) and the same connectivity / playbook patterns used by
  the Frigate integration.

Past V1 work and the open-source future versions are tracked in the GitHub
milestones and in [`docs/V1.md`](docs/V1.md).

---

## Documentation

The repository contains the full technical history and the operational
documentation:

- [**AGENTS.md**](AGENTS.md) — engineering guidelines, module boundaries,
  architecture principles and the agent workflow.
- [**docs/V1.md**](docs/V1.md) — the V1 product and implementation plan that
  produced the current codebase.
- [**docs/PLAN.md**](docs/PLAN.md) — POC and V1 technical history, including
  the Decision Log of experiments, trade-offs and known limitations.
- [**docs/RELEASE.md**](docs/RELEASE.md) — build, signing and Play Store
  readiness checklist, store listing inventory, rollback procedure.
- [**docs/REQUIREMENTS.md**](docs/REQUIREMENTS.md) — POC success criteria and
  acceptance test.
- [**docs/TOOLCHAIN_PATCH.md**](docs/TOOLCHAIN_PATCH.md) — the patched Go
  toolchain that ships `tsnet` on Android 11+.

Branding assets used in the README live under [`docs/assets/branding/`](docs/assets/branding/).

---

## License

Hearthlane is licensed under the [GNU General Public License v3.0 (GPL-3.0-only)](LICENSE).

This means that any distributed or derivative work must also be licensed
under GPL-3.0-only. The full license text is in the [LICENSE](LICENSE) file
at the root of this repository.

Third-party software notices for the components distributed with the
application are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---
