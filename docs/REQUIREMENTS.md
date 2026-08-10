# POC Requirements

## 1. Purpose

This proof of concept validates whether a family-facing Android application can provide remote live access to cameras managed by Frigate while embedding the private Tailscale connectivity required to reach the homelab.

The user must not need to install or operate the official Tailscale Android application.

## 2. Success Statement

The POC is successful when:

> A modern Android phone outside the home LAN launches the POC application, establishes its private connectivity, and displays live video from at least one Frigate camera without exposing Frigate publicly.

## 3. Target Platform

- Modern Android
- Physical Android phone required for final validation
- Kotlin application
- Jetpack Compose UI
- Debug APK is sufficient
- Play Store distribution is out of scope

The exact minimum Android SDK may be selected during project bootstrap based on current Android and dependency requirements. Avoid unnecessarily old Android support.

## 4. Existing Infrastructure Assumptions

The POC assumes an existing homelab with:

- Frigate already operational
- go2rtc configured for Frigate live view
- at least one working IP camera in Frigate
- Tailscale tailnet already operational
- remote routing/access to the homelab already proven using a normal Tailscale client
- internal DNS may exist, but the POC must not depend on search-domain behavior for its first connectivity test

The POC should prefer an explicit private hostname or address during early experiments.

## 5. Functional Requirements

### FR-001 — Application launch

The Android application shall start without requiring the official Tailscale Android application.

Acceptance criteria:

- Tailscale official app is not installed, or is disabled for the test.
- POC application launches normally.

### FR-002 — Embedded private connectivity

The application shall establish Tailscale-based private connectivity from inside the application.

Acceptance criteria:

- An embedded node or equivalent application-owned Tailscale session is created.
- The application can report whether private connectivity is connected or failed.
- Connectivity does not rely on launching another application.

### FR-003 — Enrollment

The POC shall support a development-time method to enroll the application/node into the existing tailnet.

Acceptance criteria:

- Enrollment can be completed on a physical device.
- No permanent administrator credential is hardcoded in source control.
- No reusable administrative key is packaged in the committed APK configuration.

A manual, short-lived, or one-time enrollment flow is acceptable for the POC.

### FR-004 — Frigate reachability probe

The application shall prove basic HTTP reachability to Frigate through the embedded private network before attempting video.

Acceptance criteria:

- A Frigate health/version/API request succeeds through the private path.
- Failure is visible in the UI or debug log.
- Direct public Internet exposure is not used.

### FR-005 — Camera target

The POC shall support at least one configured Frigate camera.

Acceptance criteria:

- A target camera can be selected using a development configuration value or simple hardcoded non-secret identifier.
- Camera credentials are not stored in the Android application.

Automatic camera discovery is optional.

### FR-006 — Live video

The application shall render live video for the target Frigate camera.

Acceptance criteria:

- Video comes from Frigate/go2rtc rather than direct camera RTSP access.
- The stream is visibly live rather than a periodically refreshed snapshot.
- Time to first frame is measurable.
- Playback remains usable for at least 5 continuous minutes during the final test.

### FR-007 — Remote test

The complete flow shall work outside the home LAN.

Acceptance criteria:

- Disable home Wi-Fi or test from an unrelated network.
- Use mobile data or unrelated Wi-Fi.
- Start the application.
- Establish embedded Tailscale connectivity.
- Open the target camera.
- Receive live video.

### FR-008 — Connection state

The application shall display a minimal connection state.

Required states:

- Connecting
- Connected
- Error

A production-quality status screen is not required.

### FR-009 — Retry

The user shall be able to retry after a failed private-network or Frigate connection.

A single explicit retry action is sufficient.

## 6. Streaming Requirements

Frigate uses go2rtc to provide live streaming capabilities and can use multiple live-view technologies. The POC must determine which transport is practical inside a native Android application over the embedded Tailscale path.

The experiment order should be:

1. Inspect the existing Frigate/go2rtc configuration and available live endpoints.
2. Prefer a low-latency transport supported by Frigate/go2rtc and Android.
3. Evaluate WebRTC first when integration cost is reasonable.
4. If WebRTC integration blocks the POC, evaluate the simplest Frigate/go2rtc-supported fallback that still provides genuine live video.
5. Document the selected transport and why.

Do not implement direct RTSP access to the Hikvision camera as a shortcut.

## 7. Non-Functional Requirements

### NFR-001 — Privacy

Frigate shall remain private.

No direct Internet port forwarding shall be introduced for the POC.

### NFR-002 — Least privilege

The final POC report shall identify the minimum network access the Android application requires.

Production design should restrict family application identities to the Frigate-related destinations required for viewing.

### NFR-003 — Secret handling

The repository must not contain:

- long-lived Tailscale auth keys
- Tailscale OAuth secrets
- Frigate administrator credentials
- camera credentials
- private keys

Development secrets must be external to Git.

### NFR-004 — Failure visibility

Connectivity and playback failures shall produce actionable debug information.

Logs must distinguish at least:

- Tailscale enrollment/authentication failure
- private route/DNS/reachability failure
- Frigate HTTP failure
- video signaling/transport failure
- decoder/player failure

### NFR-005 — Performance

The final remote test shall record:

- connection establishment time
- time to first video frame
- 5-minute playback stability
- reconnect behavior after one network change

Hard performance thresholds are not required for the POC.

### NFR-006 — Maintainability

Tailscale integration and Frigate integration must be separated behind small interfaces so either can be replaced after the POC.

### NFR-007 — Scope control

The POC should contain only the UI required to prove the technical flow.

A single screen is acceptable.

## 8. Out of Scope

The following are explicitly excluded:

- Nextcloud
- Immich
- family account management
- Authentik integration
- Frigate event timeline
- recordings browser
- notifications
- two-way audio
- PTZ
- multiple households
- iOS
- background monitoring
- widgets
- Android TV
- full VPN client
- VPN for other applications
- production provisioning service
- app store deployment
- analytics
- crash reporting SaaS

## 9. Technical Risks to Validate

### RISK-001 — Embedding Tailscale on Android

`tsnet` is designed to embed a Tailscale node in a Go program. The POC must validate that the chosen Tailscale code can be packaged and bridged into the Android application with acceptable build complexity.

This is the highest-priority risk.

### RISK-002 — Android networking model

Application-scoped Tailscale dialing may be sufficient for HTTP requests but may complicate media frameworks that expect normal system sockets.

If the selected video player cannot use application-owned network connections, Android `VpnService` may become necessary.

The POC must prove or reject this.

### RISK-003 — Live transport

Frigate/go2rtc supports multiple live-view paths. Browser-oriented streaming assumptions may not map directly to a native Android player.

The POC must select and validate one native-compatible path.

### RISK-004 — WebRTC networking

WebRTC can involve signaling, UDP, ICE, and multiple connection paths. The embedded Tailscale architecture must be tested rather than assumed to work.

### RISK-005 — Enrollment UX

A manual or developer-driven Tailscale enrollment can validate the POC but is not sufficient for a family product.

If the POC succeeds, production planning must separately solve secure device enrollment.

## 10. Acceptance Test

Final acceptance procedure:

1. Install a fresh debug APK on a physical modern Android phone.
2. Ensure the official Tailscale Android application is not providing connectivity.
3. Connect the phone to mobile data or a network unrelated to the home LAN.
4. Launch the POC.
5. Complete or restore POC enrollment.
6. Confirm the app reports private connectivity as connected.
7. Confirm a Frigate API probe succeeds.
8. Open the configured camera.
9. Confirm live video starts.
10. Keep the stream open for at least 5 minutes.
11. Change network once if practical (for example Wi-Fi to mobile data).
12. Retry/reconnect and confirm the live stream can be restored.
13. Record measurements and failures in `docs/PLAN.md`.

## 11. POC Exit Criteria

### GO

Recommend proceeding to an MVP when:

- embedded Tailscale networking works reliably on Android
- Frigate live video is practical through that path
- no public Frigate exposure is required
- the build/distribution complexity is acceptable
- a plausible secure provisioning approach exists

### NO-GO / REWORK

Stop or redesign when:

- embedded Tailscale requires maintaining an impractical fork
- video playback cannot use the embedded connectivity without effectively rebuilding the full Tailscale Android client
- live transport is too fragile for family use
- secure enrollment cannot be separated from embedded long-lived secrets

A no-go is a valid POC result.
