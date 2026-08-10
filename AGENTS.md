# AGENTS.md

## Project Overview

This repository contains a proof of concept for a family-friendly Android camera application that provides remote access to a private Frigate installation without requiring the official Tailscale Android application to be installed separately.

The POC is intentionally narrow. Its purpose is to validate embedded private networking and Frigate live video access on a modern Android device.

## Primary Goal

Prove this end-to-end path:

```text
Android application
    -> embedded Tailscale connectivity
    -> private tailnet / homelab
    -> Frigate / go2rtc
    -> live camera video
```

The POC succeeds when a physical Android device, while outside the home LAN and using mobile data or an unrelated Wi-Fi network, can launch the application and display live video from at least one Frigate camera through the private Tailscale path.

## Non-Goals

Do not expand the POC to include:

- Nextcloud integration
- Immich integration
- Frigate event browsing
- Frigate timeline
- push notifications
- two-way audio
- camera PTZ controls
- user management UI
- general-purpose VPN functionality for other Android applications
- full Tailscale client feature parity
- iOS support
- production-grade device provisioning
- Play Store publication
- polished UI
- offline downloads

These may be considered after the networking and live-video assumptions are validated.

## Technology Direction

Preferred application stack:

- Android
- Kotlin
- Jetpack Compose
- Android Studio / Gradle
- Go for the embedded networking component where required
- Tailscale `tsnet` or another officially supported Tailscale code path
- Frigate HTTP API
- Frigate bundled go2rtc for live streaming

Do not introduce Flutter, React Native, or a backend service unless the POC demonstrates a concrete need.

## Architecture Principles

### 1. Keep Tailscale application-scoped

The preferred design is for only this application to use the embedded Tailscale connection.

Do not create a device-wide VPN unless application-scoped networking proves insufficient for live video.

A device-wide Android `VpnService` implementation is a fallback experiment, not the default architecture.

### 2. Do not expose Frigate publicly

Frigate must remain reachable only through the private network path.

Do not:

- open Frigate ports directly to the Internet
- add a public unauthenticated reverse-proxy endpoint
- bypass tailnet access controls for convenience

### 3. Never embed long-lived administrative credentials

Do not commit or package:

- reusable Tailscale auth keys
- OAuth client secrets
- Frigate administrator passwords
- camera RTSP credentials
- private TLS keys
- homelab administrator credentials

Secrets used during the POC must be provided at build/run time or stored using an appropriate local development mechanism.

### 4. Treat provisioning as replaceable

For the POC, a manually generated short-lived or one-time enrollment mechanism is acceptable.

Production provisioning is explicitly out of scope.

Do not design a large provisioning backend before embedded connectivity is proven.

### 5. Prefer Frigate abstractions over direct camera access

The mobile application must talk to Frigate/go2rtc, not directly to Hikvision RTSP endpoints.

Frigate remains responsible for camera configuration and stream normalization.

### 6. Minimize dependencies

Every new dependency must have a concrete reason.

Avoid frameworks added only for future possibilities.

## Development Rules

- Source code, comments, logs, commit messages, and repository documentation must be written in English.
- User-facing family UI can later be localized, but localization is not part of the POC.
- Prefer small, testable modules.
- Keep networking code separated from UI code.
- Keep Frigate integration separated from Tailscale integration.
- Do not silently catch network or playback errors.
- Log state transitions useful for debugging without logging secrets.
- Prefer explicit error states over retries with no visibility.
- Avoid premature abstractions.

## Suggested Module Boundaries

```text
app/
  ui/
  navigation/

core/
  connectivity/
  frigate/
  playback/

native/
  tailscale/
```

Responsibilities:

### `core/connectivity`

Expose application-oriented connectivity state such as:

- disconnected
- authenticating
- connecting
- connected
- failed

The Kotlin UI must not depend on low-level Tailscale implementation details.

### `core/frigate`

Provide minimal operations required by the POC:

- Frigate health/version probe
- discovery or configuration of the target camera
- retrieval of live-stream metadata if required

Do not add event APIs during the POC.

### `core/playback`

Own the Android playback implementation.

Keep transport/player experimentation behind this boundary so WebRTC/MSE/HLS alternatives can be tested without rewriting the UI.

### `native/tailscale`

Contain the Go/Tailscale integration and Android bridge.

Keep this boundary very small.

## POC Milestones

Agents must work in this order unless a blocking technical discovery requires changing it:

1. Create minimal Android application.
2. Prove Kotlin-to-native/Go bridge if required.
3. Start embedded Tailscale node.
4. Authenticate/enroll the embedded node.
5. Reach a simple private Frigate HTTP endpoint.
6. Reach Frigate/go2rtc stream metadata.
7. Render one live camera stream.
8. Repeat the live-stream test outside the home LAN.
9. Record results and technical limitations.
10. Stop.

Do not continue into product features after milestone 9 without an explicit new requirement.

## Validation Requirements

A change that claims embedded connectivity works must include evidence from a physical Android device.

Emulator-only success is insufficient for the final POC result.

At minimum test:

- home Wi-Fi
- mobile network outside the home LAN

For live video, record:

- time to first frame
- whether playback remains stable for at least 5 minutes
- whether reconnect works after switching networks
- selected live transport
- relevant Android version/device
- whether traffic is application-scoped or device-wide

## Definition of Done

The POC is done when all of the following are true:

- The official Tailscale Android application is not required.
- The POC application obtains private connectivity to the homelab.
- Frigate is not exposed publicly.
- At least one configured camera displays live video remotely.
- The test succeeds while the phone is not connected to the home LAN.
- No long-lived administrative secret is embedded in the APK.
- The selected streaming approach and its limitations are documented.
- A clear go/no-go recommendation for a production app is written.

## Decision Recording

Any significant discovery must be written to `docs/PLAN.md` under the Decision Log.

Important examples:

- `tsnet` can or cannot be packaged acceptably for Android.
- a `VpnService` is required.
- WebRTC succeeds or fails over the embedded path.
- Frigate's preferred live transport changes the architecture.
- authentication requires a backend for a production implementation.

Do not hide failed experiments. Failed experiments are POC results.

## Security Expectations

Assume family devices are less trusted than infrastructure administrator devices.

The eventual tailnet policy should allow the family application identity to access only what is required for Frigate viewing.

The POC may temporarily use broader access for debugging, but the final validation must identify the minimum required network destinations and ports.

## Commands and Tooling

Prefer standard repository commands such as:

```text
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

If a Go module is added:

```text
go test ./...
go vet ./...
```

Any additional build step required to generate an Android-compatible library from Go must be documented in the repository README or build scripts rather than relying on shell history.

## Agent Behavior

When working on this repository:

1. Read `docs/REQUIREMENTS.md`.
2. Read `docs/PLAN.md`.
3. Identify the current milestone.
4. Implement only what is required for that milestone.
5. Run the relevant checks.
6. Update the Decision Log when a technical assumption is validated or rejected.
7. Do not broaden the scope without explicit instruction.

When uncertain, prefer an experiment that validates the riskiest assumption with the least code.
