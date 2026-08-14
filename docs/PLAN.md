# POC Plan

## Status

**Phase:** Phase 4 — Live Video Spike (implementation + finalization done; device validation pending)  
**Primary experiment:** Embedded Tailscale + remote Frigate live video on modern Android  
**Scope:** Technical POC only

## 1. Hypothesis

A native Android application can embed enough Tailscale functionality to privately reach a home Frigate instance and display a live camera stream without requiring installation of the official Tailscale Android application.

## 2. Why This Is a POC

Three assumptions must be proven before building product features:

1. Tailscale can be embedded into the Android application with manageable build and lifecycle complexity.
2. The networking model works with the transport/player required for Frigate live video.
3. Live playback over the private path is stable enough for non-technical family users.

Do not build a polished camera app until these assumptions are validated.

## 3. Architecture Under Test

Preferred architecture:

```text
+------------------------------------------+
| Android App                              |
|                                          |
|  +------------------+                    |
|  | Jetpack Compose  |                    |
|  +--------+---------+                    |
|           |                              |
|  +--------v---------+                    |
|  | Frigate Client   |                    |
|  +--------+---------+                    |
|           |                              |
|  +--------v---------+                    |
|  | Connectivity API |                    |
|  +--------+---------+                    |
|           |                              |
|  +--------v---------------------------+  |
|  | Embedded Tailscale / Go component  |  |
|  +------------------------------------+  |
+---------------------+--------------------+
                      |
                      | tailnet
                      v
+------------------------------------------+
| Homelab                                  |
|                                          |
|  Tailscale node / subnet access          |
|           |                              |
|           v                              |
|       Frigate                            |
|           |                              |
|           v                              |
|         go2rtc                           |
|           |                              |
|           v                              |
|      Hikvision camera                    |
+------------------------------------------+
```

### Fallback architecture

Use Android `VpnService` only if native media playback cannot operate through application-scoped Tailscale networking.

```text
Android App
    -> VpnService/TUN
    -> embedded Tailscale packet path
    -> Frigate/go2rtc
```

This fallback substantially increases complexity and must be justified by experiment results.

## 4. Work Breakdown

### Phase 0 — Repository Bootstrap

Deliverables:

- Android Gradle project
- Kotlin
- Jetpack Compose
- one activity / one screen
- `AGENTS.md`
- `docs/REQUIREMENTS.md`
- `docs/PLAN.md`
- `.gitignore`
- build instructions

Validation:

```text
./gradlew assembleDebug
```

must succeed.

Exit condition:

A blank/minimal APK runs on a physical Android device.

---

### Phase 1 — Tailscale Embedding Spike

Goal:

Prove that Tailscale code can run as part of the Android application.

Tasks:

- investigate the current official `tsnet` API and Android build constraints
- create the smallest possible Go component
- expose a minimal Kotlin-callable API
- define state directory behavior
- define startup/shutdown lifecycle
- implement development-time enrollment
- expose connectivity state

Minimal bridge shape:

```text
start(...)
stop()
status()
probe(host, port or URL)
```

Do not expose the entire Tailscale API to Kotlin.

Validation:

The app-owned Tailscale node appears in the tailnet and can reach a private target.

Exit condition:

A private TCP/HTTP probe succeeds from the physical Android device without the official Tailscale app.

Decision checkpoint:

- **PASS:** continue to Phase 2.
- **FAIL:** determine whether Android-specific Tailscale client internals or `VpnService` are required.
- **STOP:** if embedding requires an unacceptable maintained fork, document no-go.

---

### Phase 2 — Transparent Frigate Connectivity

Goal:

Prove that the app reaches Frigate transparently: directly over the home LAN
when at home, and through the embedded Tailscale network when outside the LAN,
without the user knowing or choosing a transport.

Strategy:

1. Try the normal Android network first (home LAN), with a short timeout.
2. If the local probe succeeds, use that connection and keep Tailscale stopped.
3. If the local probe fails or exceeds the timeout, start the embedded
   Tailscale node, wait for Running, and probe again exclusively over the
   tsnet network.
4. Report `CONNECTED_LOCAL`, `CONNECTED_TAILSCALE`, or `FAILED`. The UI only
   ever receives the result; it never drives the Tailscale lifecycle.

Architecture:

```text
FrigateConnectionManager
  -> LocalTransport       (normal Android network, short timeout)
  -> TailscaleTransport   (tsnet networking only, no OS-network fallback)
```

Expected flows:

```text
Home LAN:   probe local -> Frigate answers -> CONNECTED_LOCAL
            (Tailscale stays stopped)

Outside:    probe local -> timeout/failure -> start Tailscale -> Running
            -> probe via tsnet -> CONNECTED_TAILSCALE
```

Endpoint: `GET /api/version`.

Timeouts: local probe 1-2 s (keeps the out-of-home experience snappy); Tailscale
connect and remote probe bounded (45 s / 10 s).

No known-network or last-route optimizations, no auto-discovery, no
WebRTC/RTSP/HLS playback yet.

Tasks:

- minimal Frigate client in `core/frigate` (`GET /api/version`)
- fallback connection strategy with unit tests
- `HttpGet` over tsnet exposed by the Go bridge (`tsnet.HTTPClient()` dials via
  `tsdial.UserDial`, so traffic cannot fall back to the OS network)
- minimal UI: current state, LOCAL/TAILSCALE label, Frigate version, error, retry

Exit condition:

Frigate `/api/version` answers through the embedded private path outside the
home LAN and through the local network at home, with the used transport
evident in the logs and in the UI label.

Do not continue into Phase 3 without an explicit instruction.

---

### Phase 3 — Inspect Live Streaming Path

Goal:

Select the smallest viable live transport before implementing a player.

Tasks:

- inspect Frigate live configuration
- confirm go2rtc stream name for the target camera
- inspect available Frigate/go2rtc stream endpoints
- record current camera codecs
- prefer H.264 for the POC when an appropriate camera stream exists
- evaluate WebRTC signaling requirements
- identify whether the Android media stack can consume the selected transport through application-scoped networking

Deliverable:

A short Decision Log entry selecting the first transport experiment.

No UI work is required in this phase.

**Result (2026-08-11):** Transport selected. See the Decision Log entry
"Phase 3 — HLS/fMP4 selected as the first live transport experiment". In
summary: HLS served by go2rtc through the Frigate `/go2rtc/` proxy, consumed by
ExoPlayer over a tsnet-backed custom DataSource. WebRTC is deferred to the
`VpnService` fallback path (Experiment B) because Android media stacks use
OS-level sockets. No UI work was performed.

---

### Phase 4 — Live Video Spike

Goal:

Display one real-time Frigate camera.

Experiment order:

#### Experiment A — Application-scoped networking

Attempt to keep all connectivity owned by the embedded Tailscale component.

Questions:

- Can the signaling/API requests be dialed through the embedded connection?
- Can the media transport use the same networking mechanism?
- Can the player receive video without a device-wide VPN?

If yes, this is the preferred architecture.

#### Experiment B — `VpnService` fallback

Only run this experiment if Experiment A fails specifically because the Android media stack requires ordinary system networking.

Questions:

- Can a TUN-based Android VPN path route only the POC's required traffic?
- Can the existing Tailscale code be reused instead of reimplementing WireGuard/Tailscale behavior?
- What additional lifecycle/background-service requirements appear?

Exit condition:

One camera shows genuine live video on the physical Android device.

**Implementation status (2026-08-11):** Experiment A (application-scoped HLS) is
coded end to end and builds/tests/lints green; physical-device playback
validation is pending.

- `core/connectivity` gained the generic HTTP transport primitive
  (`HttpBytesGetter`, `HttpBytesResult`): a full GET returning status, content
  type, final URL and body, never falling back to another network.
- `native/tailscale/go` exposes `HttpGetBytes` (gomobile `HttpResult`); the
  Go bridge and `TsnetGatewayImpl.httpGetBytes` map it into
  `HttpBytesResult`, and `TsnetHttpBytesGetter` routes Tailscale media
  requests exclusively through the tsnet path.
- `core/frigate` selects the getter per transport (`bytesGetterFor`:
  `HttpUrlConnectionBytesGetter` for LOCAL, `TsnetHttpBytesGetter` for
  TAILSCALE) and resolves the first go2rtc stream (`GET /api/go2rtc/streams`,
  order-preserving JSON key parser, no JSON dependency) plus the HLS URL
  `/api/go2rtc/api/stream.m3u8?src={name}&mp4`. The `/api/go2rtc/` prefix is
  required on Frigate 0.17.1; `/go2rtc/...` is caught by the web UI SPA.
- `core/playback` owns ExoPlayer: `HttpBytesDataSource` (media3 `DataSource`
  that runs every GET through the injected getter and serves the buffered
  body) + `HlsMediaSource.Factory` + `LiveStreamPlayer` exposing a
  `PlaybackStatus` flow. Whole-response buffering is acceptable because every
  HLS request is a bounded playlist/segment; documented as a limitation.
- The app UI (`HomeScreen` `LiveView`) discovers the stream, renders a
  `PlayerView`, shows status, and stops/restarts playback. `usesCleartextTraffic`
  is enabled for the POC because both the LAN and tailnet Frigate origins are
  plain HTTP.

Unit tests: transport selector never lets TAILSCALE touch the OS network,
non-2xx and empty-stream handling, deterministic stream-name parsing, and the
media3 `DataSource.Factory` wiring.

**Device checklist for validation (run after APK install):** time to first
frame, 5-minute stability, network-switch reconnect, transport label, and
whether playback is application-scoped (no `VpnService` prompt).

#### Phase 4 Finalization (2026-08-11)

Implementation is complete and every code-level gate is green
(`test`, `lint`, `assembleDebug`). The remaining work is device validation.

##### Diagnostics for the acceptance measurements

The app records the Phase 4/Phase 5 measurements with no new infrastructure
(logcat + a UI line; no metrics backend):

- **Time to first frame:** `LiveStreamPlayer` anchors a wall clock at `play()`
  and logs `first frame rendered <N>ms after play request` when the first
  frame is rendered; exposed as `PlayerMetrics.firstFrameElapsedMs`.
- **Playback errors:** every `Player.Listener.onPlayerError` increments
  `PlayerMetrics.errorCount` and includes the running count in the log line.
- **Bytes transferred:** the HLS data source reports the body size of every
  GET (playlists, init, media segments) through a callback, accumulated in
  `PlayerMetrics.bytesTransferred`.
- **Session summary:** `LiveStreamPlayer.release()` logs the final metrics
  (first frame, errors, bytes), making each player instance's lifetime
  auditable.
- **Transport:** the probe logs `transport selected: LOCAL|TAILSCALE` and
  `transport switched #N: X -> Y`; network events log
  `network transition detected: onAvailable|onLost`.
- **Player lifecycle:** `player created for transport=...` and
  `player released (live view left or transport switched)` bracket every
  player instance.
- The `LiveView` shows a `Diagnostics:` line (first frame / errors / bytes)
  under the video so the numbers can be read on screen during the tests.

##### Lifecycle guarantees (verified by code review + unit tests)

- **Player destruction:** `LiveView` releases the player in a
  `DisposableEffect`, so leaving the live view (connection lost) or switching
  transport tears the player down. The `PlayerView` re-binds via its `update`
  lambda, so a recreated player never leaves a dead surface.
- **Tailscale start/stop:** `FrigateConnectionManager` starts the node only
  when the local probe fails, and the connection path stops it when a later
  local connection wins. Unit tests assert that LOCAL never starts the node
  and repeated LOCAL successes keep it stopped (no start/stop loops).
- **Controlled error propagation:** transport failures surface as
  `IOException` from `HttpBytesDataSource` (so ExoPlayer's retry policy
  applies), discovery/probe failures surface as explicit UI errors, and
  enrollment failures surface the login URL. The UI is Play/Stop only; there
  is no Pause (see the "Pause does not stop media3 network activity" decision
  log entry).
- **Request lifecycle:** every HLS GET is bounded by the request timeout, the
  whole-response buffer is released on `DataSource.close()`, and releasing
  ExoPlayer cancels in-flight loaders.

##### Streaming bridge (documented)

The media path is a request/response bridge, not a streaming API:

```text
ExoPlayer -> HttpBytesDataSource (media3 DataSource)
  -> HttpBytesGetter (core/connectivity)
    -> TsnetHttpBytesGetter / HttpUrlConnectionBytesGetter (core/frigate)
      -> TsnetGateway.httpGetBytes (native/tailscale)
        -> Go HttpGetBytes (gomobile) -> netstack TCP dial through the tunnel
```

- Every request buffers its full body in memory before being handed to
  ExoPlayer. This is only acceptable because each HLS request is bounded: one
  playlist, one init, or one media segment (tens of KB to a few MB). It must
  not be used for progressive or long-lived streams. That limitation is
  documented and `core/playback` is the swap boundary if another transport is
  needed later.
- Redirects are followed inside the Go bridge and the final URL is reported
  (`HttpBytesResult.finalUrl`), so relative HLS segment references resolve
  correctly through `DataSource.getUri()`.
- The TAILSCALE getter can only dial through the tunnel (netstack TCP via the
  tailnet/subnet route, split-DNS resolved via the tailnet resolver); the
  LOCAL getter uses only the Android network. Neither path can fall back to
  the other.

##### Physical acceptance checklist

Run on a physical Android device with the official Tailscale app disabled:

- **TEST A — Home LAN (LOCAL):** on home Wi-Fi, the transport shows LOCAL,
  the node stays stopped, and playback stays up for 5 minutes. Read time to
  first frame from the `first frame rendered` log.
- **TEST B — Outside the home LAN (TAILSCALE):** on mobile data or unrelated
  Wi-Fi, the transport shows TAILSCALE; measure connect time, time to first
  frame, and 5-minute stability.
- **TEST C — Network switch:** drop home Wi-Fi during playback and confirm the
  `network transition detected` and `transport switched` logs, video recovery,
  and the diagnostics line; then switch back.
- **TEST D — Stop and lock/unlock:** press Stop (the media source is
  released; bytes must stop) and Play to re-establish a fresh session; lock
  and unlock the screen and confirm the stream re-establishes on unlock and
  that no bytes flow while the screen is off.
- **TEST E — Process and identity:** kill and relaunch the app; the node
  identity must survive (no re-enrollment). Only an invalidated identity
  should require re-enrollment via the login URL.

Record the Phase 5 template (device, Android version, network, times,
stability, issues) for each test.

---

### Phase 5 — Remote Acceptance Test

Goal:

Validate the actual family use case.

Environment:

- physical Android phone
- not connected to home LAN
- mobile data or unrelated Wi-Fi
- official Tailscale app not providing connectivity

Procedure:

1. Launch POC.
2. Wait for private connectivity.
3. Probe Frigate.
4. Start live camera.
5. Measure time to first frame.
6. Leave playback running for 5 minutes.
7. Observe freezes/rebuffer/reconnect events.
8. Change network once if practical.
9. Retry and restore playback.
10. Capture logs.

Record:

```text
Device:
Android version:
Network:
Tailscale architecture:
Frigate version:
Camera:
Codec:
Transport:
Connect time:
Time to first frame:
5-minute stability:
Network-change result:
Known issues:
```

Exit condition:

Enough evidence exists for a go/no-go decision.

---

### Phase 6 — POC Conclusion

Write a concise conclusion in this file.

Required questions:

- Did embedded Tailscale work without the official app?
- Did it require `VpnService`?
- Which Frigate/go2rtc transport worked?
- Was playback stable?
- What is the estimated complexity of production provisioning?
- What minimum tailnet access is required?
- Is this architecture suitable for a family-facing MVP?

Stop implementation after answering these questions.

## 5. Initial Technical Decisions

### TD-001 — Native Android

**Decision:** Kotlin + Jetpack Compose.

**Reason:** Android is the only target of the POC and native networking/media integration is a core risk.

### TD-002 — Application-scoped Tailscale first

**Decision:** Attempt embedded application-owned networking before `VpnService`.

**Reason:** The product needs private connectivity for this app, not a general-purpose VPN for the entire phone.

### TD-003 — Frigate remains the camera gateway

**Decision:** The app does not connect directly to Hikvision RTSP URLs.

**Reason:** Frigate/go2rtc already owns camera configuration, restreaming, and live-view concerns.

### TD-004 — One camera only

**Decision:** Use one existing Frigate camera for the POC.

**Reason:** Multiple-camera UX adds no evidence about the core technical hypothesis.

### TD-005 — No production provisioning yet

**Decision:** Use the smallest secure-enough development enrollment mechanism that does not commit a reusable administrative secret.

**Reason:** Building provisioning infrastructure before proving embedded networking would invert the risk order.

## 6. Open Questions

These questions must be answered by implementation experiments, not speculation.

- What is the cleanest supported way to package current Tailscale Go code for modern Android?
- Is `tsnet` itself sufficient in the Android process, or does the mobile environment require code paths closer to the Tailscale Android client?
- Can the selected native video stack use application-scoped Tailscale dialers?
- Which Frigate/go2rtc live transport is simplest and stable on Android?
- Does WebRTC require UDP/network behavior that pushes the design toward `VpnService`?
- How should node state persist across Android process restarts?
- What happens when the app moves between Wi-Fi and mobile networks?
- What exact tailnet grants/ACLs are required for Frigate live viewing?

## 7. Risk Register

| Risk | Impact | Priority | Mitigation |
|---|---|---:|---|
| Tailscale embedding is not Android-friendly | POC blocker | Critical | Spike before Frigate work |
| Media stack cannot use app-scoped dialer | Architectural change | Critical | Test early; `VpnService` fallback |
| WebRTC complexity | Schedule | High | Keep alternate Frigate/go2rtc live transports available |
| Codec incompatibility | Playback failure | High | Prefer existing H.264-compatible stream for POC |
| Enrollment requires secret in APK | Security blocker | High | Use one-time/ephemeral development provisioning |
| Android process lifecycle kills network state | Reliability | Medium | Persist state and test restart |
| Mobile network switching breaks stream | UX | Medium | Explicit retry; measure before automating recovery |
| Scope expansion | Delayed result | High | Enforce `AGENTS.md` non-goals |

## 8. Decision Log

Append decisions here as experiments complete.

### 2026-08-14 — TAILSCALE playback instability root cause: per-segment tailnet DNS re-resolution; DNS cache + TCP-first policy

**Symptom:** During live playback over TAILSCALE, ExoPlayer reported
`ERROR_CODE_IO_UNSPECIFIED: ... HttpBytesDataSource: GET
http://site.omni.corp/api/go2rtc/api/hls/segment.m4s?id=... failed:
tsembed: tailnet DNS lookup for "site.omni.corp": tsembed: dial dns upstream
192.168.10.2:53 over tcp: context deadline exceeded`.

**Root cause (confirmed):** every HLS request (manifest or segment) goes through
`HttpGetBytes` -> `resolveHost`, which re-resolved the hostname over the tunnel
on every request (no DNS cache). `site.omni.corp` is split-DNS served by the
homelab resolver `192.168.10.2:53`, which is known to ignore UDP over the subnet
route, so each resolution burned the 2s UDP wait and then a fresh TCP dial
through netstack. Under mobile/DERP conditions the TCP dial occasionally
exceeded its 2s budget, failing the segment fetch and surfacing as
`ERROR_CODE_IO_UNSPECIFIED`. The failure at `segment.m4s ... n=30` (late in a
session that had been fetching segments fine) shows the path itself works and
the failure is the per-segment re-resolution cost.

**Fix:**

- **In-memory DNS cache in `tsembed`:** thread-safe, process-lifetime, never
  persisted to disk. Keyed by normalized hostname (lowercase, no trailing dot).
  The value is the list of resolved A records plus an expiry. The TTL is derived
  from the DNS answer (minimum TTL across A records), clamped to a conservative
  maximum of 60s; **a zero-TTL answer falls back to the fixed 60s TTL** instead
  of disabling the cache (the homelab resolver always advertises TTL 0, see
  follow-up below). Failures and empty answers are never cached. Cache misses
  are serialized under a mutex with a post-lock re-check so concurrent segment
  fetches collapse into a single resolution (no thundering herd).
- **TCP-first for private split-DNS upstreams:** upstreams classified as private
  (RFC1918, loopback, IPv6 ULA, and the tailnet CGNAT range 100.64.0.0/10) are
  queried over TCP first because the homelab resolver ignores UDP over the
  subnet route; UDP remains a fallback for resolvers that only answer UDP.
  Public upstreams keep the standard UDP-then-TCP order, mirroring the official
  client's forwarder. The 2s per-transport budget is unchanged for now.
- **Observability:** resolution succeeded/failed and the chosen transport are
  logged at the normal level (once per TTL, not per segment); cache hit/miss are
  logged at debug level, gated by the new `SetDebugDNS` binding, which the
  Android bridge enables in debug builds. No tokens, auth URLs or other secrets
  are logged.

**Validation:** `go test ./...` and `go vet ./...` green (new tests cover: first
resolution queries and caches; second resolution within TTL is a cache hit with
no new query; TTL expiry re-queries; resolution errors are not cached; empty
answers are not cached; zero-TTL answers fall back to the fixed cache TTL;
concurrent misses for the same hostname collapse into a single query;
per-hostname entries are independent; concurrent reads/writes do not corrupt
the cache; private upstream is TCP-first with UDP fallback; public upstream
stays UDP-first with TCP fallback; answer TTL is the minimum across A records
and capped at 60s).
`./gradlew test lint :app:assembleDebug` green. Rebuilt AAR exposes
`setDebugDNS`.

**Follow-up (physical validation, 2026-08-14):** the on-device log showed the
cache was active but ineffective: the homelab resolver `192.168.10.2:53`
answers **TTL 0 for every A record**, which the initial policy treated as
"do not cache", so per-segment re-resolution persisted. Fixed by falling back
to the conservative fixed TTL for zero-TTL answers (cache now actually caches).
The same log showed three concurrent `cache miss` lines at startup, so a simple
per-hostname miss serialization was added (mutex + post-lock re-check).

**Status:** NOT marked resolved. Pending physical-device validation: LOCAL ->
TAILSCALE switch during live playback, 10-15 minutes of remote playback with no
new tailnet DNS timeouts or `ERROR_CODE_IO_UNSPECIFIED`, TAILSCALE -> LOCAL
switch still working, and the transport label matching the real transport. If
TCP dial timeouts persist with the cache active, report before raising the 2s
budget.

### 2026-08-14 — LiveView recovery policy: auto-recover everything except 5xx/404; explicit Retry button

**Context:** over TAILSCALE the live view auto-recovers from every playback
error by creating a fresh go2rtc session. A 5xx (server-side problem) or a 404
(missing resource) is not fixed by a fresh session, so retrying them silently
only spins a recovery loop with no visibility.

**Decision:** playback errors now carry the HTTP status of the failing response
when one exists (`PlaybackStatus.Error.statusCode`, extracted from the
`HttpStatusIOException` raised by `HttpBytesDataSource` and walked out of the
Media3 `PlaybackException` cause chain). The auto-recovery effect in `LiveView`
recovers only when the error is **not** 404 and **not** 5xx (`autoRecoverable`);
connection-level failures (timeout, DNS, refused) have no status and stay
auto-recoverable. The TAILSCALE-unbounded / LOCAL-bounded (2, resets after 10s
stable) budget logic is unchanged. A **Retry** button was added to the live view
for both the playback-error and the stream-discovery-error states, so the
non-recoverable cases surface the error with a manual action instead of a
black screen.

**Validation:** `:core:playback:testDebugUnitTest` green (new tests cover the
`autoRecoverable` classification: null/403/429/418 recoverable, 404/500/503
not; and `httpStatusFrom` walks the Media3 cause chain and returns null for
connection failures). Full `./gradlew test lint :app:assembleDebug` green.

**Follow-up (physical validation, 2026-08-14):** with the policy active, a
manual Retry on a failed live view did not recover, while leaving the live view
and re-entering did. Re-entry builds a fresh `LiveStreamPlayer` (new
`ExoPlayer`), whereas Retry reused the same instance that had just suffered a
fatal error. Fix: `LiveStreamPlayer.play()` now hard-resets the same instance
before re-preparing (`stop()` + `clearMediaItems()` when `STATE_IDLE`), making
a Retry behave like a freshly created player, and a re-discovery failure that
happens while a stale stream URL is still set is now shown under the player
instead of hiding behind a black surface. The re-enter-only-works symptom was
the tell that the player restart, not the network, was the failing step.

### 2026-08-14 (later) — LiveView recovery policy reversed: auto-recover everything, zero error UI

**Context:** the 5xx/404 gate above meant some failures surfaced a "Playback
error" message and a Retry button. The user asked for fully automatic recovery:
during an error, the screen must show **nothing different** from normal
playback, and the error counter must never appear.

**Decision:** the policy is **reversed**. Every playback error now auto-recovers
with a fresh go2rtc session (TAILSCALE unbounded / LOCAL bounded, unchanged,
including 5xx/404), and a failed discovery auto-retries itself after a 1.5s
backoff (`DISCOVERY_RETRY_DELAY_MS`). All Retry buttons were removed from the
live view. Error text is never composed: the footer label masks
`PlaybackStatus.Error` as "Playing", the re-discovery banner under the player
was deleted, and `metrics.errorCount` was dropped from the diagnostics line
(the `errors` placeholder was removed from `live_view_metrics`). The
`discoveryError` state and the `CopyableError` helper became dead code and were
removed from `LiveView`. `PlaybackStatus.Error.autoRecoverable` was removed as
dead code; `statusCode` stays only for the recovery log. The "Camera
unavailable" product state (a genuinely missing stream) keeps its explicit "Try
again" — auto-retry must not loop on a product state. Only "unavailable" and
the transport header/log still carry error context; the metrics line keeps
"recoveries" so on-device tests can still tell recovered sessions apart.

**Validation:** full `./gradlew test lint :app:assembleDebug` green.

### 2026-08-14 — Network-change listener must stay active while live video is shown

**Context:** with recovery working, a device test found that entering the live
view over Tailscale and then turning on Wi-Fi did **not** switch to LOCAL; the
connection stayed on Tailscale even though the LAN had become reachable. This
had worked in the POC.

**Root cause:** the network callback (`FrigateConnectionController.start()`) was
scoped to the `Screen.Home` branch's `DisposableEffect`. The POC embedded the
live view inside Home, so the listener stayed registered while watching. When
the live view became a separate destination (V1.3), navigating Home -> Live
disposed the Home branch, whose `onDispose` called `controller.stop()` and
unregistered the callback. On the Live screen there was therefore **no
network-driven re-probe**: turning on Wi-Fi never produced an `onAvailable`
handling, `connect(false)` never ran, the connection stayed
`Connected(TAILSCALE)`, and `LiveView` (keyed on `transport`) never rebuilt on
the LOCAL getter.

**Fix:** `AppRoot` now registers the controller (network callback) in the
`Screen.Live` branch as well, with the same `start()/stop()` lifecycle as Home.
The `registerDefaultNetworkCallback` also fires `onAvailable` for the current
default network at registration, so entering the live view performs one
cheap local-first re-probe (self-heals a home-but-Tailscale case) and every
later network transition re-probes normally. A successful LOCAL re-probe updates
`connection` -> `Connected(LOCAL)`; `LiveView`'s `remember(transport, gateway)`
then rebuilds the player on the LOCAL getter and the reconnect effect starts
playback — the POC transport-switch behavior is restored.

**Validation:** full `./gradlew test lint :app:assembleDebug` green. The fix is
behavioral (composition-root wiring) and will be confirmed on the physical
device: enter live view over Tailscale, enable home Wi-Fi, verify logcat shows
the re-probe and the switch (`transport switched #N: TAILSCALE -> LOCAL`) and
that playback restarts over LOCAL.


### 2026-08-12 — V1.2 camera discovery implemented over the existing LOCAL/TAILSCALE transport path

**Context:** V1.1 gates the app on first-run administrator setup. V1.2 must
prove that Frigate's real `/api/config` payload can be parsed into a minimal
camera model and observed by the app. The POC network/playback stack is
reused unchanged.

**Validation against the real Frigate 0.17.1 payload:**

- `GET /api/config` returns the full FrigateConfig `model_dump(mode="json",
  exclude_none=True)`. Cameras live under the top-level `cameras` object, keyed
  by the camera name.
- The camera key (the dictionary key) is the stable identifier; the inner
  `name` field equals the key and is ignored by the parser.
- The friendly name field is `friendly_name` (String, optional). With
  `exclude_none=True`, an unset friendly name is **omitted** from the payload
  rather than serialized as `null`; explicit `null` is still tolerated.
- The enabled state field is `enabled` (Boolean, default `true` on the
  `CameraConfig` model). All cameras in the current install carry it.
- Current install payload: 4 cameras (`backyard`, `hall`, `garage`, `gate`),
  each with a friendly name; `garage` has `enabled: false`.
- `/api/config` is guarded by `allow_any_authenticated()`. It allows anonymous
  requests only when Frigate authentication is disabled (the current install).
  With Frigate auth enabled, this endpoint would require a JWT from `/auth`.
  This is recorded as a product risk: V1 camera discovery assumes auth is
  disabled or that credentials will be added in a later milestone.

**Decision:**

- The `Camera` domain model contains `id`, `displayName`, `enabled` and
  `playable`, matching the V1.2 task specification. `snapshotUrl` from the
  broader V1 conceptual model is intentionally deferred to V1.3 (Home UI /
  thumbnails).
- Display-name rule: `friendly_name` when present and non-empty; otherwise the
  camera key, with no title-case or underscore-to-space transformation.
- Disabled cameras (`enabled: false`) are excluded at the discovery boundary,
  per docs/V1.md section 6.4 ("Disabled: excluded from Home"). The `enabled`
  field is still parsed and carried on the model because the task mandates it.
  A configuration with only disabled cameras yields the `Empty` state, which is
  valid.
- Parsing is dependency-free: a small JSON scanner (`CameraConfigJson`) in
  `core/frigate` extracts `cameras`, iterates keys, reads `friendly_name` and
  `enabled`, and tolerates extra/unknown fields of arbitrary nesting. This keeps
  the module JVM-testable without adding a JSON library.
- Discovery is transport-agnostic: `FrigateCameraDiscovery` takes an injected
  `HttpBytesGetter`. The app selects the getter with the existing
  `bytesGetterFor(transport, gateway)` function, so LOCAL discovery uses the
  OS-network getter (Tailscale never involved) and TAILSCALE discovery routes
  through the existing `TsnetGateway` with no duplicated HTTP logic.
- **Playable resolution (camera -> go2rtc stream):** after the enabled cameras
  are parsed from `/api/config`, discovery requests `GET /api/go2rtc/streams`
  and collects the full set of top-level stream names (`Go2RtcStreams
  .streamNames`, order-preserving, no JSON dependency). Each enabled camera is
  `playable` iff its `id` (the config key, which is also the go2rtc stream
  name on the real install) belongs to that set. Matching is always by exact
  camera id; stream order or a "first stream" pick never influences the
  result. The real-install relation camera key == stream name is the only one
  proven so far; if a divergence is ever observed on a real payload, it is
  reported rather than guessed (open question, see Pending below).
- A camera enabled in the config but without a matching stream stays in the
  `Loaded` result with `playable = false`; it is never removed. `Empty` still
  means no enabled camera at all, and the absence of streams never turns the
  whole discovery into `Empty` or `Error`. This is what lets the future Home
  show "Camera unavailable" per docs/V1.md section 6.4.
- **go2rtc endpoint failure:** if `/api/config` succeeds but
  `/api/go2rtc/streams` fails (non-2xx, transport error, or invalid body), the
  discovered cameras are preserved with `playable = false` and the failure
  message is recorded in `CameraDiscoveryState.Loaded.streamsWarning` for
  diagnostics, instead of losing the whole list. Rationale: `/api/config` is
  the authoritative camera source, and a streams outage should degrade to
  "nothing playable" rather than hide the cameras. The controller logs the
  warning via logcat; no UI change was made in V1.2.
- The provisional `LiveView` playback spike still uses `Go2RtcStreams
  .firstStreamName` and was **not** changed: V1.2 has no camera selection UI,
  so there is no per-camera routing to conflict with, and the first-stream pick
  is a documented spike behavior until V1.3 routes a selected camera to
  playback.
- The `CameraDiscoveryController` exposes `Loading/Loaded/Empty/Error` states,
  runs discovery when the shared connection becomes `Connected` with a new
  transport, and supports a manual Refresh that re-discovers without
  re-probing the connection. No continuous polling was added.
- Logs contain only sanitized operational data (transport, camera count,
  success/failure, streams warning). The full `/api/config` and
  `/api/go2rtc/streams` payloads are never logged.

**Validation:** `go test ./...`, `go vet ./...`, `./gradlew clean test lint
:app:assembleDebug` all green. New unit tests cover display-name resolution
(present, absent, null, empty, whitespace-only), enabled parsing, disabled
exclusion, empty configs, extra-field tolerance, invalid payloads, HTTP and
transport errors, LOCAL vs TAILSCALE getter routing, controller state machine
behavior (discovery on connect, transport switch, refresh, failure, stop/
cancel), and the playable matrix: enabled + matching stream -> `playable=true`,
enabled without stream -> `playable=false` and kept, multiple cameras with a
subset of streams, disabled exclusion, no enabled cameras -> `Empty`, empty
streams -> `Loaded` all `playable=false`, streams endpoint failure -> cameras
preserved with `streamsWarning`, and matching by id rather than stream order /
first stream.

**Validated (physical device, 2026-08-12):** the V1.2 acceptance criteria were
confirmed on the real install on both LAN and outside the home LAN. The app
resolved exactly the enabled cameras (`backyard`, `hall`, `gate`) and excluded
the disabled `garage` camera. Friendly names rendered in the provisional list
and the camera key fallback was not needed. Every enabled camera had a matching
go2rtc stream by exact key/name equality, so all resolved `playable=true` on
both transports. No divergence between camera key and stream name was observed;
if that ever occurs on another install, the mapping rule must be decided from
the observed payload rather than guessed.

### 2026-08-12 — V1.3 family-facing Home screen implemented with per-camera navigation and transport-routed thumbnails

**Context:** V1.2 resolved the camera list from Frigate. V1.3 builds the Home
UX around that list: a grid of cards with friendly names, snapshot thumbnails
and playable/unavailable states, plus the navigation shell to a per-camera Live
View. The POC playback logic is reused unchanged.

**Decision:**

- `HomeScreen` is rewritten as a family-facing camera grid. It no longer shows
  infrastructure details (URL, transport, version, raw errors). Visible states
  use product language only: "Connecting", "Live", "Camera unavailable",
  "Try again".
- Each card shows the Frigate `friendly_name` (camera key fallback), a
  best-effort snapshot thumbnail, and an overlay when the camera is not
  `playable`. Tapping a playable card navigates to `Screen.Live(camera.id)`.
- The `Screen.Live` route and `LiveScreen` container are introduced in V1.3 so
  the Home grid can route by camera id. The actual per-camera playback refactor
  (replacing `Go2RtcStreams.firstStreamName()` with the selected camera's
  stream) is intentionally left to V1.4; V1.3 only adds the route shell and
  reuses the existing `LiveView` spike.
- Thumbnails are loaded with Coil, but **no `coil-network-*` dependency** is
  added. Instead a custom `FrigateSnapshotFetcher` (`app/thumbnail`) implements
  Coil's `Fetcher` and routes every snapshot request through the existing
  `HttpBytesGetter` selected by the current transport (`bytesGetterFor`). This
  keeps snapshot traffic on the same LOCAL/TAILSCALE path as the rest of the
  app and reuses the proven `HttpBytesGetter` primitive.
- Memory caching uses `FrigateSnapshotKeyer` with a stable key derived from
  camera id, base URL and a `refreshKey`. Manual refresh bumps the key so a
  fresh snapshot is fetched; normal navigation benefits from the in-memory
  cache. No disk cache is configured beyond Coil's defaults.
- `CameraDiscoveryController` exposes a `refreshKey` that is bumped on manual
  refresh so the thumbnail model changes and Coil fetches a new snapshot.
- Enrollment-during-network-switch bug fixed: when a Live session loses LOCAL
  connectivity and the Tailscale fallback requires enrollment, the `AppRoot`
  `Screen.Live` branch now renders a neutral "Connecting" placeholder and lets
  the global `LaunchedEffect(authRequired)` push `Screen.Setup`. Previously the
  branch called `navigateBack()` on any `Failed` connection, which raced with
  the enrollment routing and often left the user on a failed Home screen. The
  fix is a pure navigation decision (`LiveDestination` / `liveDestination()`)
  that is unit tested.

**Validation:** `./gradlew test lint :app:assembleDebug` green; Go checks
(`go test ./...`, `go vet ./...`) green. New tests cover card/grid rendering
(`CameraCardTest`, `CameraGridSectionTest`), thumbnail fetcher/keyer
(`FrigateSnapshotTest`, `FrigateSnapshotKeyerTest`,
`CameraThumbnailModelFactoryTest`), Live navigation decision
(`AppRootLiveDestinationTest`), and the existing discovery/setup suites.

**Validated (physical device, 2026-08-12):**

- Home grid renders the enabled cameras (`backyard`, `hall`, `gate`) with
  Frigate friendly names and snapshot thumbnails on both LOCAL (home Wi-Fi) and
  TAILSCALE (mobile data outside the home LAN).
- The disabled `garage` camera is not shown.
- Unavailable cameras (simulated by removing a stream) show the
  "Camera unavailable" overlay.
- Manual refresh updates thumbnails without re-probing the connection.
- **LOCAL -> remote enrollment:** with the embedded node not yet enrolled, the
  user opened a camera on home Wi-Fi (LOCAL), then disabled Wi-Fi and switched
  to mobile data. The app detected the network loss, re-probed, hit the
  Tailscale auth-required state, and correctly routed to the enrollment screen
  instead of falling back to a failed Home state. After completing enrollment
  the Live view resumed over TAILSCALE.

### 2026-08-14 — V1.4 per-camera live playback: the selected camera owns the stream

**Context:** V1.3 routed the Home grid to `Screen.Live(camera.id)` but the
Live View spike still played a "first stream". V1.4 removes that pick: the
Live screen now plays exactly the go2rtc stream whose name equals the
selected camera id, the only relation proven on the real install (V1.2
Decision Log).

**Decision:**

- `Go2RtcStreams.firstStreamName` and `firstTopLevelKey` are removed; the new
  `streamNameForCamera(baseUrl, cameraId, timeout)` resolves the stream by
  exact `camera.id == stream name` equality and returns `null` when no stream
  matches. Stream order never influences playback, and a missing stream is
  never silently replaced by another camera's stream.
- `LiveView` receives the `cameraId` chosen on Home and resolves/plays that
  camera's stream. The `lastStreamName` cache is replaced by a `streamResolved`
  flag: recovery of a dead go2rtc session reuses the fixed name (`== cameraId`),
  while any discovery failure invalidates the flag so a removed stream
  self-heals by re-listing on the next attempt.
- A selected camera whose stream is absent from `/api/go2rtc/streams` now
  surfaces an explicit "Camera unavailable" state with a Try again action
  instead of a dead player (product state, not an infrastructure error).
- `AppRoot` camera resolution for the `Screen.Live` route is extracted into the
  pure, unit-tested `resolveLiveCamera(cameraId, selectedCamera,
  discoveryState)`: the camera tapped on Home wins; otherwise the
  already-discovered list is consulted by exact id (covers process restart).
  No new global discovery is triggered.
- `LiveScreen` titles the screen with the Frigate `displayName` (friendly
  name, camera key fallback), so the family-facing title never shows a raw
  camera id when a friendly name exists. The duplicated in-body live-view title
  is removed.
- Robolectric/Compose tests (`LiveViewTest`) prove the selected camera resolves
  its own stream (`src=<cameraId>`), the unavailable state, and that leaving
  the live view releases the player. Because the Compose test activity is only
  injected into the **debug** app manifest, `LiveViewTest` runs on the debug
  unit tests; `testReleaseUnitTest` excludes that one class (the Compose
  contract is fully covered by the debug variant, release runs the non-UI
  suites).

**Validation:** `./gradlew test lint :app:assembleDebug` green. New tests:
`TransportAndGo2RtcTest` (streamNameForCamera by exact id for multiple
cameras, order independence, missing stream -> null, empty streams -> null,
non-2xx propagation), `ResolveLiveCameraTest` (selected camera wins, exact-id
fallback, friendly display name preserved, unknown id -> null, discovery not
loaded -> null), and `LiveViewTest` (per-camera stream resolution, unavailable
state, player release on leave).

**Validated (physical device, 2026-08-14):** V1.4 physical validation completed
successfully — all 10 planned scenarios passed on a real Android install. In
particular:

- **Per-camera playback by `cameraId` validated:** the selected camera plays
  its own go2rtc stream (`src=<cameraId>`), never a "first stream".
- **LOCAL -> TAILSCALE validated:** leaving the home LAN falls back to the
  embedded Tailscale node without interaction.
- **TAILSCALE -> LOCAL validated:** re-enabling home Wi-Fi re-probes
  local-first and switches back to LOCAL (network listener active while live
  video is shown; see the network-listener entry above).
- **Tailscale stopped after LOCAL confirmed:** the embedded node is released
  when the local probe succeeds.
- **Playback stability validated:** 5-minute stable live video, network-switch
  recovery, back navigation stops bytes, rotation keeps playback, time to
  first frame logged, and automatic invisible recovery works.
- **Known limitation (recorded, not blocking):** rotating the Live View to
  landscape clips part of the content (no scroll/adaptive layout for the
  shorter available height); playback continues, no crash and no
  networking/lifecycle regression. Registered as UI/polish debt for a later
  milestone (V1.md Section 16).

### 2026-08-12 — V1.1 first-run setup implemented without touching the POC network/playback stack

**Context:** V1.0 shipped the screen-capable foundation (shared connection
controller, persisted settings, sealed-class navigation, sanitized diagnostics
report). V1.1 adds the first-run administrator setup gate.

**Decision:**

- `AppSettings` gains a single new persisted flag `setup_complete`
  (`booleanPreferencesKey`, Preferences DataStore). The app gates on it: while
  false only the setup screen shows; after it is set, normal use goes straight
  to Home and never asks about infrastructure again. No new persistence layer.
- A new pure `SetupFlow` state machine (`app/setup`) models the gate
  (`shouldShowSetup`) and the probe transitions: `EnterConfig` -> `Testing` ->
  `Connected` | `EnrollmentRequired` | `Failed`. The probe is injected; the
  production probe is `FrigateConnectionController.testConnection(url)`, which
  reuses the exact `FrigateConnectionManager` construction shared by
  `connect()` (extracted into `runConnect`) — no connectivity or enrollment
  logic is duplicated.
- `Screen.Setup` is the only new route. As the first-run gate it has no back
  stack entry (system back does nothing); reopened from Home's discreet
  Settings button it pushes/pops normally. The shared controller is only
  started (network callback) inside the Home branch, so no background re-probe
  runs while the admin configures the server.
- The URL is committed to settings only when the admin finishes after a
  successful probe, so abandoning the reopened screen leaves the existing
  configuration intact; editing the URL after a result invalidates it
  (`SetupFlow.reset`), so an untested value can never be saved.
- Enrollment stays the proven interactive flow (probe -> `TailscaleAuthRequired`
  -> URL in the result). The URL is displayed and opened only in the explicit
  admin enrollment section (`SetupScreen.EnrollmentSection`); it is never
  written to logcat (existing guarantee re-verified). Setup failure text stays
  simple; the raw error is only exposed via the copy action, and failures are
  recorded in the controller's `lastError` for the future Diagnostics screen.

**Validation:** `go test ./...`, `go vet ./...`, `./gradlew clean test lint
:app:assembleDebug` all green (30 app-module unit tests). New tests cover the
setup state machine (success, enrollment then retry, failure keeps setup open,
in-flight guard, URL invalidation), the setup-complete persistence round-trip,
the navigation route, and the existing sanitized-diagnostics guarantee. APK
scan confirms no `tskey-` auth key and no enrollment URL (a concrete
`login.tailscale.com/a/...` link) in the app code; the bare
`login.tailscale.com` constant in `libgojni.so` is the Tailscale library's own
interactive-auth host, unchanged from V1.0.

**Validated (physical device, 2026-08-12):**

- **Setup gate:** a fresh install lands on the setup screen and nothing else
  is reachable until setup completes; after completion the app opens straight
  to Home and never asks about infrastructure again.
- **setupComplete persistence:** completing setup persists the flag (Preferences
  DataStore); killing and relaunching the app, and reinstalling over, both skip
  setup and go to Home.
- **Re-entry via Settings:** Home's discreet Settings button reopens the setup
  screen with the existing Frigate URL pre-filled; the saved configuration is
  intact, testing the URL works, and saving returns to the app.
- **Enrollment/fallback:** the interactive enrollment flow (probe ->
  `TailscaleAuthRequired` -> login URL) works; the "Open login URL" action
  completes enrollment and the following test succeeds. LOCAL and TAILSCALE
  behave exactly as in V1.0.
- **No enrollment/auth URL in logs:** `adb logcat` across the full setup and
  enrollment run shows no enrollment or auth URL. The app code never logs it
  (re-verified); the only `login.tailscale.com` occurrence is the library
  constant above.

### 2026-08-12 — V1.0 product foundation implemented without touching the POC network/playback stack

**Context:** The POC (embedded Tailscale + transparent Frigate connectivity +
HLS/fMP4 live view) is code-complete; the product plan in `docs/V1.md` defines
the first family-facing version. V1.0 is the screen-capable foundation: it must
not change any networking or playback behavior.

**Decision:**

- `app/ui/HomeScreen.kt` was split: the connection/session state (connect
  strategy, transport tracking, network-driven re-probe with settle delay,
  connect/network ticks) moved into
  `app/controller/FrigateConnectionController`, which still calls
  `FrigateConnectionManager` unchanged. The network callback lifecycle moved
  from a `DisposableEffect` inside HomeScreen to `controller.start()/stop()`.
- `app/navigation/Screen.kt` adds the sealed-class navigation (`Screen.Home`
  shell only; Live View/Diagnostics/Settings arrive in later milestones).
- `app/settings/AppSettings.kt` persists the Frigate URL and the internal node
  hostname suffix with AndroidX Preferences DataStore (plaintext, per
  docs/V1.md section 12); the URL is seeded from the build-time default and no
  longer uses `rememberSaveable`.
- The node hostname is now `family-camera-<suffix>` (suffix generated once and
  persisted), replacing the POC `poc-camera`; it is an internal value only.
- `app/diagnostics/DiagnosticsReport.kt` builds the sanitized copy report from
  an allow-listed `Snapshot`; free-form strings pass through `sanitize` which
  redacts Tailscale login/control-plane URLs and token-shaped runs. Unit tests
  assert the report never contains the enrollment URL or token-shaped strings.
- The enrollment auth URL is no longer written to logcat (already removed from
  `TsnetGatewayImpl`/`TailscaleTransport`; the "read it from logcat" hint was
  dropped from `auth_url_pending_hint`). The URL still flows to the UI for the
  interactive enrollment action.
- Reused unchanged: `core/connectivity`, `core/frigate`, `core/playback`, the
  Go `tsembed` module and `native/tailscale` bridge. `LiveView` was only moved
  out of HomeScreen into its own file; playback logic is identical.

**Validation:** `go test ./...`, `go vet ./...`, `./gradlew test lint
:app:assembleDebug` all green; new unit tests cover the settings round-trip and
diagnostics sanitization plus the navigation stack. APK scan confirms no
`tskey-` auth key and no enrollment URL are packaged.

**Pending (physical device):** install-over the current APK; re-run the POC
scenarios (LOCAL, TAILSCALE, network switch, lock/unlock, rotation) and confirm
logcat never shows the enrollment URL.

### 2026-08-11 — Over TAILSCALE the live view recovers from every playback error; request failures are logged and retried

**Context:** On-device testing over Tailscale showed a few playback errors
(recoveries fired) while LOCAL had zero. The live view should always recover
over the VPN path, and logcat should make the errors diagnosable (handled by
the app vs. a real connection problem).

**Decision:**

- The auto-recovery is now unbounded over TAILSCALE: every
  `PlaybackStatus.Error` re-discovers a fresh go2rtc session (reusing the
  cached stream name), so a dead stream never leaves a black screen. LOCAL
  keeps the bounded budget (`MAX_AUTO_RECOVERY`) because a genuine failure
  there is not expected.
- `HttpBytesDataSource.open()` now catches getter/bridge failures (timeout,
  DNS, refused) that gomobile surfaces as generic exceptions, logs them with
  the URL (`HLS GET <url> failed (network/bridge): ...`) and rethrows them as
  `IOException`, so ExoPlayer's transient retry policy applies instead of
  treating them as fatal. Non-2xx and payload-validation failures were already
  logged; this closes the gap for connection-level errors.
- The live view diagnostics line now also shows a `recoveries` count, so
  on-device tests can tell handled recoveries from unhandled errors without
  logcat.

**Validation:** `./gradlew test lint :app:assembleDebug` green. On-device
verification over Tailscale must confirm the video keeps coming back after
errors and logcat shows the per-request failure categories (timeout vs. 404
vs. empty payload).

### 2026-08-11 — Pause does not stop media3 network activity; the UI is now Play/Stop only

**Symptom (device test):** pressing Pause during live playback kept the
`Diagnostics: bytes` counter increasing, while Stop stopped it immediately.

**Root cause:** the Pause button called `player.player.pause()`, which only
sets `playWhenReady = false`. In media3 that does NOT release the HLS media
source: the playlist tracker keeps polling the m3u8 while the period is
loaded, and go2rtc keeps its HLS session alive as long as requests keep
arriving, so segments keep being produced (and possibly fetched until the
load-control target buffer is reached). Stop (`player.stop()`, `STATE_IDLE`)
releases the media source and cancels the loaders, which is why it stopped
the bytes. This also invalidated the earlier assumption that "pause stops
ExoPlayer's playlist fetches so the go2rtc session dies on its ~5s
keepalive": while paused the session stayed alive, and the same applied to
the `ON_STOP` screen-off path (`LiveStreamPlayer.pause()`), which therefore
could still burn mobile data in the background.

**Decision:**

- The UI is now Play/Stop only; the Pause button and the `userPaused` flag
  were removed. Play always re-discovers a fresh go2rtc session, Stop
  releases the media source.
- `LiveStreamPlayer.pause()` was replaced by `LiveStreamPlayer.stop()`
  (`player.stop()`), used by both the Stop button and the `ON_STOP`
  screen-off path, so no bytes flow while the screen is off. A fresh session
  is created on resume (`resumeTick`) as before.

**Validation:** `./gradlew test lint :app:assembleDebug` green. On-device
verification must confirm: Stop freezes the bytes counter, Play restores
video, and the screen-off path no longer consumes data.

### 2026-08-11 — Phase 4 finalization: on-device diagnostics and acceptance checklist

**Context:** The POC code (transport, discovery, playback, recovery) is
complete and all code-level gates are green, but the Phase 4 exit condition
(live video on a physical device) and the Phase 5 acceptance measurements
(time to first frame, 5-minute stability, network-switch reconnect) still
need on-device evidence.

**Decision:** Add measurement support without new infrastructure, and pin the
device procedure in `PLAN.md`:

- `core/playback` exposes `PlayerMetrics` (time to first frame, error count,
  bytes transferred) with log lines for `first frame rendered`, the running
  error count, and a per-session summary on `release()`. The bytes counter is
  fed by the HLS data source via a callback threaded through
  `HttpBytesDataSourceFactory`.
- The app logs `transport selected`/`transport switched`, network
  transitions, and player create/release, and shows a `Diagnostics:` line in
  the live view.
- `PLAN.md` now contains the "Phase 4 Finalization" section: the streaming
  bridge documentation (whole-response GETs through the Go bridge and why it
  is acceptable for HLS), the lifecycle guarantees (player teardown,
  Tailscale start/stop, controlled error propagation), and the physical
  acceptance checklist TEST A–E (home LAN, outside home, network switch,
  pause/lock-unlock, process/identity).

**Validation:** `./gradlew test lint :app:assembleDebug` green. On-device
validation pending per TEST A–E.

### 2026-08-11 — Pause must be user intent and transport switches must rebind the player surface

**Context:** The second on-device validation round (cellular, Wi-Fi, and
Wi-Fi/cellular switches) exposed two remaining failures:

1. **Paused video restarted by itself on both networks, and mobile playback
   recycled HLS sessions.** The `ConnectivityManager` re-probe on every network
   event bumped `connectAttempt`, and the live view re-discovered and called
   `play()` (which forces `playWhenReady=true`) on every bump. Cellular
   handovers fire `onLost`/`onAvailable` frequently, so mobile playback
   restarted constantly; a paused video was restarted by the same path.
   Disabling auto-recovery while paused was not enough: the real restart source
   was the network-driven re-discovery.
2. **After any transport switch (Wi-Fi → Tailscale → Wi-Fi) the video went
   black and never recovered, even across stop/reconnect.** The player is
   recreated with `remember(transport, gateway)` when the network path changes,
   but the `PlayerView` AndroidView had no `update` lambda: its `factory`
   bound the view once to the original player, which was released on the
   switch. The view stayed bound to a dead player forever.

**Decision:** `HomeScreen.LiveView` was reworked around explicit user intent and
network-path identity:

- **App-owned controls:** the ExoPlayer controller is disabled
  (`useController=false`). `userPaused` records a real user pause via the app's
  Pause button and is only cleared by its Play button. Every auto-trigger
  (screen unlock, network switch, session recovery, re-probe) is gated on
  `!userPaused`, so a paused video stays paused until the user commands Play.
  The lifecycle pause on `ON_STOP` never sets `userPaused`, so unlocking still
  resumes playback.
- **Network re-probe no longer restarts healthy playback:** `connect()` takes a
  `restartPlayback` flag. The network callback probes with `false` and bumps a
  new `networkTick`; the live view uses `networkTick` only to recover a dead
  session (`PlaybackStatus.Error` or `STATE_IDLE`), never to restart a healthy
  one. The manual Connect button and the initial load keep `connectAttempt`
  (force restart). A 1s settle delay coalesces the `onLost`/`onAvailable`
  bursts around a switch.
- **Player surface rebinding:** the AndroidView gains an `update` lambda
  (`view.player = player.player`), so a transport switch that recreates the
  player re-binds the view to the live player instead of a released one.
- The `playWhenReady` flow was removed from `LiveStreamPlayer` (now unused).

**Validation:** `./gradlew test lint :app:assembleDebug` green; APK contains the
`net.interfaceTableAndroid` symbol. On-device verification must confirm: paused
video stays paused across network switches and lock/unlock until Play is
pressed; mobile playback stops recycling on handovers; Wi-Fi → Tailscale →
Wi-Fi switches recover the video; stop/reconnect restores playback after a
switch.

### 2026-08-11 — Recovery must be pause-safe and resume-driven (supersedes the first auto-recovery entry)

**Context:** The first auto-recovery design restarted the video on every player
error. On-device use showed this was wrong in two ways:

1. Pausing stops ExoPlayer's playlist fetches, so the go2rtc session dies on its
   ~5s keepalive and the paused playlist refresh 404s. The auto-recovery treated
   that expected expiry as a mid-playback failure and restarted the video while
   the user had it paused ("se dou pause, o vídeo volta sozinho", "a cada N
   minutos o vídeo reinicia").
2. A `discovering` boolean that silently skipped overlapping discoveries could
   swallow the unlock/network re-discovery when a background recovery attempt was
   in flight, leaving a black player screen after unlocking.

**Decision:** `HomeScreen.LiveView` now splits the two concerns:

- **Pause-safe auto-recovery:** the recovery effect only fires when
  `playWhenReady` is true; an error surfaced while paused is expected session
  expiry and is never auto-restarted (the UI shows a "Paused" label instead of
  an error). The budget (`MAX_AUTO_RECOVERY` = 2) renews only after a session
  played stably for `STABLE_PLAY_MS` (10s), so a flapping stream stops retrying
  and surfaces its error while repeated pause/resume cycles always recover.
- **Resume-driven re-establishment:** a `LaunchedEffect` observing the player's
  `playWhenReady` flow re-discovers a fresh session when a manual resume meets a
  dead session (`PlaybackStatus.Error` or `Player.STATE_IDLE`); a READY session
  resumes seamlessly. Screen lock/resume and network switches keep using
  `resumeTick`/`connectAttempt`.
- **Latest-discovery-wins:** the `discovering` boolean was removed. Each
  `discoverAndPlay()` cancels and supersedes any in-flight discovery, so a
  lifecycle/network resume is never silently swallowed by a stale background
  attempt. Cancellation is rethrown (not swallowed into `discoveryError`).

**Validation:** `./gradlew test lint :app:assembleDebug` green. On-device
verification must confirm: pause >5s then resume recovers (~2-3s) without the
video auto-restarting while paused; unlocking never leaves a black screen; a
paused-then-network-switched session still recovers.

### 2026-08-11 — Re-enrollment after identity loss must surface the login URL

**Context:** After a fresh install-over of the netlink-fixed APK, the app
reported `Tailscale requires authentication` with no login link on screen.
The embedded node's persisted identity (`filesDir/tailscale/tailscaled.state`)
was no longer accepted by the control plane (node likely removed in the admin
console or the state file lost), so tsnet returned to `NeedsLogin`.

**Decision:** Two fixes, both on the path from `NeedsLogin` to a visible
enrollment action:

1. `TsnetGatewayImpl.ensureRunning` kept throwing `TailscaleAuthRequired` on
   the first `AUTHENTICATING` poll, but tsnet publishes `AuthURL`
   asynchronously, so the link was almost never captured. It now keeps polling
   in `AUTHENTICATING` until a non-blank URL appears (bounded by the connect
   timeout), and only then fails with `TailscaleAuthRequired(url)`; a plain
   connect timeout still reports the original timeout error unless the node was
   seen authenticating.
2. The UI now gets an explicit `authRequired` flag propagated through
   `FrigateTransportResult.Failure` -> `FrigateConnection.Failed`, so the
   failed screen can show either the enrollment URL with an **Open login URL**
   button (`ACTION_VIEW`) or, when the URL is still unavailable, a hint telling
   the user to retry or read logcat.

**Validation:** `./gradlew test lint :app:assembleDebug` green. The re-auth
itself is expected POC behavior: with no long-lived credential embedded, an
invalid node identity requires a manual interactive enrollment again. On-device
verification must confirm that re-enrolling restores connectivity and that the
node identity survives later app restarts and network switches.

### 2026-08-11 — Patched Go toolchain is now the default for `assembleDebug`

**Context:** After the lock/unlock and network-switch fixes were built with the
standard Go toolchain, the physical-device test regressed to
`tsnet: route ip+net: netlinkrib: permission denied` (the Phase 1 Android 11+
blocker): `./gradlew assembleDebug` was still documented to use the standard
toolchain, and the AAR built by the previous unpatched run was packaged into the
APK. The player never appeared because `FrigateConnectionManager.connect()`
returned `Failed`.

**Decision:** `native/tailscale/build.gradle.kts` now resolves the patched
toolchain at `<repo>/go-patched` as the default when `bin/go` is present; an
explicit `-PgoToolchainRoot` still wins, and environments without a patched
toolchain fall back to the standard Go toolchain (CI-safe). The goBind task's
cache input reflects the resolved toolchain path. README updated: the standard
`./gradlew assembleDebug` now produces a device-working APK; the fallback case
(no patched toolchain) is documented as compiling but failing `tsnet.Start()` on
physical Android 11+ devices.

**Validation:** `./gradlew :app:assembleDebug` (no property) re-ran goBind with
`PATCHED_GOROOT` set and produced an APK whose `libgojni.so` contains
`net.interfaceTableAndroid` (the CL 507415 fix). Full `test`/`lint`/`assembleDebug`
green.

### 2026-08-11 — Live playback must reconnect when the Android network changes

**Symptom:** Dropping home Wi-Fi (phone moves to mobile data / the embedded
Tailscale path) reproduced the pre-lock/unlock playback failure: the video died
and did not recover.

**Root causes:**
1. `connect()` ran only once (or on manual retry), so the transport stayed
   `LOCAL` after the Wi-Fi drop and the local getter kept failing with nothing
   watching for the network switch.
2. Latent bug: `LiveView` created its `LiveStreamPlayer` with a bare
   `remember {}`, so the player captured the first getter (LOCAL). Even a
   transport switch would have kept media requests on the dead local getter.

**Fix:**
- `HomeScreen` registers a default `NetworkCallback` (`onAvailable`/`onLost`)
  that re-runs the transparent `connect()` (local-first, Tailscale fallback),
  so a Wi-Fi drop automatically migrates the connection to the embedded tailnet
  path. The callback calls the latest `connect` via `rememberUpdatedState`.
- `LiveView` now keys the player by `(transport, gateway)` so a transport switch
  rebuilds the player on the matching getter, and re-runs discovery on a new
  `connectAttempt` key even when the transport stays the same (e.g. local Wi-Fi
  flaps). The re-discover-and-play path reuses the lock/unlock fix above.
- Network switch testing is now possible without killing the app (Phase 5
  checklist item).

**Validation:** `./gradlew test lint :app:assembleDebug` green. On-device
Wi-Fi-drop validation pending.

### 2026-08-11 — Playback must re-establish the go2rtc HLS session on resume

**Symptom:** After the phone screen is locked and unlocked, live video does not
return on its own; the app must be killed and reopened.

**Root cause:** go2rtc drops an HLS session on its ~5s keepalive. While the
screen is off the Activity goes to background and the SurfaceView is destroyed,
so ExoPlayer stops consuming segments and the session expires; on unlock the
media playlist URL still carries the stale `id=` and the player ends in an
error state that the UI only recovers from by restarting the whole flow.

**Fix:**
- `MainActivity` sets `FLAG_KEEP_SCREEN_ON` so the screen timeout never
  interrupts live playback while the app is foregrounded (manual lock still
  works and is covered by the observer below).
- `LiveStreamPlayer` gained `pause()` to stop fetching while the app is in the
  background.
- `LiveView` observes the Activity lifecycle: `ON_STOP` pauses the player, and
  every `ON_RESUME` re-runs the go2rtc discovery (`firstStreamName` +
  `resolveMediaPlaylistUrl`, which creates a fresh session) and restarts
  playback. A first version only re-discovered when the player was not
  `STATE_READY`, but the player stays `STATE_READY` even after the HLS session
  has expired, so a long suspension still broke playback: the resume path now
  always creates a new session (short lock/unlock cycles pay a ~1-2s restart
  instead of a dead stream). This also covers the network-switch reconnect case
  from Phase 5.

**Validation:** `./gradlew test lint :app:assembleDebug` green. On-device test
passed on the physical device (Samsung SM-S721B): after a ~10-20s screen lock
the live view returns on unlock without restarting the app.

**Validation:** `./gradlew test lint :app:assembleDebug` green. Device
re-validation of lock/unlock and Wi-Fi->mobile transitions pending.

### 2026-08-11 — Frigate 0.17.1 serves go2rtc under `/api/go2rtc/`, not `/go2rtc/`

**Symptom:** On the physical device the live view failed before playback with
`HLS master is not an m3u8 playlist (HTTP 200, type=text/html)` where the body
was a web SPA embedding the Monaco editor (`self["MonacoEnvironment"]`, with
`editor.worker.bundle.js` / `yaml.worker.bundle.js` workers).

**Root cause (reproduced from the dev container against the real Frigate):**
`GET /api/version` answers Frigate on the `site.omni.corp` origin, but a request
to `GET /go2rtc/streams` (without the `/api/` prefix) is caught by the Frigate
0.17.1 web UI SPA and returns HTML with HTTP 200 and `Content-Type: text/html`.
The Frigate/go2rtc paths used by the spike were based on the documented prefix
`/go2rtc/`, which is wrong for this version. The working prefix on this install
(confirmed by direct requests to `192.168.10.13:5000` and through the NPM
proxy on `site.omni.corp`) is:

- stream list:  `GET /api/go2rtc/streams` -> JSON `{backyard:{...}, hall:{...}, ...}`
- HLS master:   `GET /api/go2rtc/api/stream.m3u8?src={name}&mp4` -> `#EXTM3U`
  master referencing `hls/playlist.m3u8?id=...` (relative, resolves under the
  same `/api/go2rtc/api/` prefix)
- media playlist, init and segments: `/api/go2rtc/api/hls/playlist.m3u8?id=...`,
  `/api/go2rtc/api/hls/init.mp4?id=...`, `/api/go2rtc/api/hls/segment.m4s?id=...&n=...`
  (all verified: 200, `video/mp4`/`video/iso.segment`)

The `?src=backyard` camera and the fMP4 output (`&mp4`, `avc1.640029`) work
without transcoding.

**Fix:** `Go2RtcStreams` now builds `$baseUrl/api/go2rtc/streams` and
`$baseUrl/api/go2rtc/api/stream.m3u8?src={name}&mp4`; relative references in the
master/media playlists still resolve correctly because they are resolved against
the final URL by both `Go2RtcStreams.resolveMediaPlaylistUrl` and ExoPlayer's
`HttpBytesDataSource`. Unit tests updated to the `/api/go2rtc/` prefix.

**Validation:** `./gradlew :core:frigate:test :core:playback:test` green.
Playback on the physical device still needs to be re-run.

### 2026-08-11 — Phase 4 Experiment A: HLS/fMP4 spike implemented with a getter-routed DataSource

**Decision:** Implement Experiment A with every playback HTTP request routed
through the embedded tsnet path via a generic transport primitive, keeping the
media stack and the networking stack fully decoupled.

**Design:**

- `core/connectivity.HttpBytesGetter` / `HttpBytesResult` is the only media
  transport API. Implementations never fall back to another network: the tsnet
  getter (`core/frigate.TsnetHttpBytesGetter` -> `TsnetGateway.httpGetBytes` ->
  `native/tailscale` -> Go `HttpGetBytes`) dials only through the tunnel, and
  the local getter (`HttpUrlConnectionBytesGetter`) uses only the Android
  network. `bytesGetterFor(transport, gateway)` constructs exactly one getter
  per transport, so a TAILSCALE connection cannot accidentally reach the OS
  network.
- Go now exposes `HttpGetBytes` returning `HttpResult` (status, content type,
  final URL, body) so binary HLS segments travel through the same path as the
  manifest. `HttpResult` follows redirects and preserves the final URL, which
  `HttpBytesDataSource.getUri()` reports to ExoPlayer so relative segment URLs
  resolve against the real location.
- `core/playback.HttpBytesDataSource` is a media3 `DataSource` that buffers the
  whole GET response and serves it to ExoPlayer. Non-2xx responses are not
  thrown: the status is available via `getResponseHeaders`/`open` behavior and
  playback errors surface through ExoPlayer's `Player.Listener`.
  **Limitation (documented):** whole-response buffering is only acceptable
  because HLS requests are bounded (one playlist or one media segment per
  request); it must not be used for progressive or long-lived streams.
- `core/frigate.Go2RtcStreams` discovers the first go2rtc stream via
  `GET /go2rtc/streams` using an order-preserving top-level-key parser (no JSON
  dependency; `org.json` key order is unspecified) and builds
  `/go2rtc/api/stream.m3u8?src={name}&mp4` (HLS/fMP4, preferred by ExoPlayer;
  MPEG-TS is the fallback by dropping `&mp4`).
- The app UI shows a live `PlayerView` with transport label and status; the
  Android manifest enables `usesCleartextTraffic` because both the LAN and the
  tailnet Frigate origins are plain HTTP (POC-only; production should use the
  reverse-proxy/TLS guidance or Frigate TLS).

**Validation:** `go vet`/`test`, `./gradlew test`, `:app:assembleDebug` and
`lint` pass. Unit tests cover: the TAILSCALE selector never constructing the
local getter, stream discovery parsing and empty/non-2xx handling, and the
media3 factory wiring. Rebuilt AAR contains `httpGetBytes`/`HttpResult` for
arm64 and x86_64.

**Open items:** physical-device playback is unproven (time to first frame,
5-minute stability, network-switch reconnect). go2rtc HLS is known to "differ
from the standards and may not work with all players"; if ExoPlayer rejects the
manifest, fall back to MPEG-TS HLS (drop `&mp4`), then go2rtc progressive
`stream.mp4`, then Experiment B (`VpnService` + WebRTC).

### 2026-08-10 — Phase 2: transparent Frigate connection strategy implemented

**Decision:** Implement the Phase 2 objective as a transparent fallback:
`FrigateConnectionManager` probes `GET /api/version` first over the normal
Android network (`LocalTransport`, `HttpURLConnection`, 2 s timeout) and only on
failure starts the embedded node and probes again over tsnet
(`TailscaleTransport`). Results are `CONNECTED_LOCAL`, `CONNECTED_TAILSCALE`, or
`FAILED`; the UI only receives the result.

**Guarantee that the Tailscale probe never falls back to the OS network:** the
Go binding exposes `tsembed.HttpGet`, which uses `tsnet.Server.HTTPClient()`.
That client's transport is `DialContext: s.Dial`, which dials via
`tsdial.UserDial` (`net/tsdial/tsdial.go:598-604`): for a Tailscale route
(100.x tailnet IP or a subnet route advertised into the tailnet) it dials
through the tailnet peer dialer; only public internet destinations would use
the system dialer. The configured Frigate destination is always a private
tailnet/subnet-routed address, so the probe is tunneled.

**Notes:**
- Timeout rationale: 2 s local (requirement 1-2 s; keeps out-of-home fallback
  snappy and is generous for LAN), 45 s for the node to reach Running, 10 s for
  the remote probe.
- Enrollment stays interactive: a pending enrollment surfaces `FAILED` with the
  `login.tailscale.com` URL in the UI.
- Fallback strategy unit tests cover: local success (Tailscale not probed),
  local failure (Tailscale probed once), local timeout (Tailscale probed),
  Tailscale success -> `CONNECTED_TAILSCALE`, both fail -> `FAILED` with the
  enrollment URL forwarded.

### 2026-08-10 — tsnet split-DNS hostnames fail unless resolved via the tailnet resolver

**Symptom:** On the physical device, `GET http://site.omni.corp/api/version`
over the Tailscale path failed with `no such host` even though the node was
Running and the portal has `omni.corp` served by the homelab DNS.

**Root cause:** `tsnet.Server.HTTPClient()` dials via `tsdial.Dialer.UserDial`
(`net/tsdial/tsdial.go`). For a hostname that is not a MagicDNS name it falls
back to `net.DefaultResolver` (the Android system DNS), which does not know
`omni.corp`. The tailnet DNS manager — which knows the portal's nameservers
(split-DNS / homelab DNS) — is never consulted for outbound dials.

**Fix:** `tsembed.HttpGet` no longer uses `tsnet.Server.HTTPClient()`. It now
(1) resolves the hostname with the tailnet resolver
(`tsnet.Server.Sys().DNSManager.Resolver().Query`, `net/dns/manager.go`,
`net/dns/resolver/tsdns.go`), which answers MagicDNS names and forwards
split-DNS domains to the homelab DNS over the tunnel, and (2) dials the
resulting IP with a custom `http.Transport.DialContext` backed by `s.Dial`
(`tsdial.UserDial`/netstack), keeping the request Host header set to the
hostname. `github.com/miekg/dns` was added to the module for the query; it is
already a transitive dependency of tailscale.

**Validation:** The Frigate URL is now a single value used by both paths, and
the same hostname works on Wi-Fi (falls back to Tailscale when the network's
DNS does not answer) and over the tailnet (split-DNS through the homelab DNS).

### 2026-08-10 — tailnet DNS forwarder cannot reach the homelab DNS from embedded Android

**Symptom:** With the DNS fix above, the IP resolves but the probe fails with
`waiting for response or error from [192.168.10.2]: context deadline exceeded`.
On the same phone, enabling the official Tailscale app makes DNS work.

**Root cause:** In the embedded `tsnet` node the tailnet DNS resolver reaches
upstream nameservers with OS sockets, not through the tunnel:
- `resolver/forwarder.go` `sendUDP` uses a raw packet listener that falls back
  to `stdNetPacketListener` (a standard OS UDP socket) when the OS-level
  `initListenConfig` is absent, which is the case in `tsnet`.
- `sendTCP` uses `getDialerType()`; on Android `ShouldUseRoutes` only returns
  `UserDial` when the node has the `user-dial-routes` node attribute enabled in
  the admin console (`controlknobs.go`), so it defaults to `SystemDial`.
The official app works because it installs a device-wide TUN, so the OS sockets
above are routed into the tailnet. The embedded node does not change the OS
routing table.

**Fix:** `tsembed.resolveHost` no longer calls the resolver's `Query` (which
forwards through the forwarder). It now reads the upstream resolvers for the
name from `res.GetUpstreamResolvers(fqdn)` and sends the DNS query over a
netstack socket (`tsnet.Server.ListenPacket`), which routes subnet destinations
through the tunnel (`netstack.go` `ProcessSubnets`, enabled by `tsnet.go`). A
system-resolver fallback covers hostnames not handled by the tailnet DNS.

**Validation:** `go vet`/`test`/`build` green; patched-toolchain APK rebuilt;
symbols `tsembed.resolveHost` and `tsembed.queryTunnelDNS` present in the APK.
Device re-test pending.

### 2026-08-11 — tunnel sockets must bind to the tailnet IP and dial TCP via netstack

**Symptom:** On the physical device the domain lookup now failed with an error
roughly "lookup the domain, resolve over UDP, the address must be a valid IP".
By-IP also stopped working, on both mobile data and Wi-Fi.

**Root causes:**
1. `tsnet.Server.ListenPacket("udp4", ":0")` rejects the empty host:
   `resolveListenAddr` normalizes `""`, `0.0.0.0` and `::` to the zero
   `netip.Addr`, and `ListenPacket` then returns `address must be a valid IP`
   (`tsnet.go`). The DNS socket must bind to the node's tailnet IP
   (`tsnet.Server.TailscaleIPs`).
2. `tsnet.Server.Dial` (`tsdial.UserDial`) dials subnet-routed IPs with the
   system dialer on Android: `UseNetstackForIP` only covers tailnet IPs and the
   route table is only consulted when `ShouldUseRoutes` is true (requires the
   `user-dial-routes` node attribute). By-IP dials to LAN Frigate IPs therefore
   never entered the tunnel.

**Fix:** The DNS socket now binds to the node's tailnet IP instead of `:0`, and
HTTP dials no longer use `s.Dial`: `tsembed` dials TCP directly through the
netstack implementation (`sys.Netstack.Get().(*netstack.Impl).DialContextTCP`),
which routes tailnet IPs and subnet routes through the tunnel regardless of the
node attribute. `req.Host` is still set to the requested hostname.

**Validation:** `go vet`/`test`/`build` green; patched-toolchain APK rebuilt
with symbols `tsembed.dialNetstackTCP`/`tsembed.queryTunnelDNS`; Kotlin UI gains
a "Copy error" button. Device re-test pending.

### 2026-08-11 — homelab DNS ignores UDP over the subnet route; TCP DNS fallback

**Symptom:** Device re-test: by-IP now works over the tunnel on mobile data
(netstack TCP dial confirmed), and Wi-Fi works for both IP and domain. The
domain still failed over mobile data with
`read dns answer from 192.168.10.2:53 over udp: i/o timeout`.

**Root cause:** The netstack UDP query reached the homelab DNS path (write
succeeded) but no answer came back, while netstack TCP through the same subnet
route works. The homelab resolver at `192.168.10.2` does not answer the plain
UDP query coming through the subnet router.

**Fix:** `tsembed` now falls back to DNS-over-TCP against the same upstream
through the tunnel (`udpDNSQuery` → `tcpDNSQuery`), mirroring the official
client's UDP-then-TCP forwarder behavior. TCP over the subnet route is already
proven by the by-IP Frigate dial.

**Validation:** `go vet`/`test`/`build` green; patched-toolchain APK rebuilt
with symbol `tsembed.tcpDNSQuery`. Device re-test pending.

### 2026-08-11 — Phase 3: HLS/fMP4 selected as the first live transport experiment

**Findings:**

- Frigate 0.17.1 bundles go2rtc and reverse-proxies its HTTP API under
  `/go2rtc/` on the Frigate origin (current Frigate docs: `GET /go2rtc/streams`,
  `GET /go2rtc/streams/{name}`, and go2rtc's own API paths such as
  `/go2rtc/stream.m3u8?src=...`). The same origin already proven in Phase 2
  (`site.omni.corp`) exposes these paths with no Frigate-native auth on this
  install.
- go2rtc live transports reachable through Frigate: MSE (fMP4 over the
  WebSocket path), WebRTC (port 8555 TCP/UDP, requires ICE candidates; the
  Frigate docs note that a Tailscale IP must be added as a candidate for
  Tailscale access), HLS (`/go2rtc/stream.m3u8?src=...`, MPEG-TS by default,
  fMP4 with `&mp4`), and jsmpeg (detect stream; browser-only, CPU-heavy; not a
  native-Android candidate).
- **Application-scoped constraint (decisive):** Android media/WebRTC stacks
  (libwebrtc via `mediastreamer`/`webrtc-android`, and similarly player
  libraries) create OS-level sockets that cannot be pointed at the embedded
  tsnet dialers. WebRTC media therefore cannot traverse the embedded tunnel
  without a device-wide `VpnService`. This answers the open question
  "Does WebRTC require network behavior that pushes the design toward
  `VpnService`?": yes, unless a library exposes a custom transport.
- HLS is plain HTTP request/response (manifest plus per-segment GETs), so it
  reuses the proven Phase 2 tunnel path (netstack TCP through the subnet route)
  and is consumed by ExoPlayer through a custom `DataSource` that performs its
  GETs via the Go bridge. No `VpnService` required. Latency is segment-bound
  (~1-3 s), acceptable for a family live view, and the `core/playback` boundary
  allows swapping to WebRTC later without UI changes.
- Target camera is H.264 (main and sub) per the user, so no video transcode is
  needed for ExoPlayer/MediaCodec. go2rtc repackages PCMA/PCMU/PCM audio to
  FLAC for HLS/fMP4 (`&mp4=flac`); audio is optional for the POC.

**Decision:** First live-transport experiment (Phase 4 Experiment A) is HLS via
the go2rtc proxy — `http://site.omni.corp/go2rtc/stream.m3u8?src=<camera>&mp4`
(fMP4/H.264) — played with ExoPlayer (media3) using a custom `DataSource` whose
HTTP requests dial through the embedded tsnet path. The camera/stream name is
resolved at runtime from `GET /go2rtc/streams` (first available stream). WebRTC
remains documented as the `VpnService`-dependent Experiment B if HLS proves
unusable.

**Open items before Phase 4 coding:**
- go2rtc's HLS "differs from the standards and may not work with all players";
  validate the manifest in ExoPlayer first. Fallbacks: MPEG-TS HLS (no `&mp4`),
  go2rtc progressive `stream.mp4`, then Experiment B.
- go2rtc stream names confirmed (user config): `backyard`, `backyard_sub`,
  `hall`, `hall_sub`, `garage`, `garage_sub`, `gate`, `gate_sub` (four cameras,
  each with a `_sub` variant). Source URLs are masked in the user's paste and
  are not needed by the app. Presence of an ffmpeg audio transcode is still
  unconfirmed and will be read from `GET /go2rtc/streams` at runtime; audio is
  optional for the POC.
- The Go bridge currently exposes `HttpGet` returning a `String`; HLS segment
  bodies are binary, so a bytes variant (or short-lived byte stream) is needed.
  Per-request full bodies are acceptable because each HLS request is a bounded
  response.

### 2026-08-11 — Phase 2 device validation passed on mobile data

The TCP DNS fallback made the domain work end to end on the physical device
outside the home LAN: `site.omni.corp` over the embedded Tailscale path
connected and the Frigate version probe (`0.17.1-416a9b7`) succeeded, with the
same URL working on Wi-Fi (LAN path) and by-IP over mobile data. The embedded
node resolves split-DNS via the homelab DNS (`192.168.10.2`) over the tunnel,
using UDP with a TCP fallback, and dials both DNS and HTTP through netstack so
subnet-routed destinations never fall back to the Android system dialer.

Milestone 6 (reach Frigate HTTP over the embedded tailnet) is now proven on a
physical device. Live-stream rendering is the next milestone.

### 2026-08-10 — Phase 1: device log shows netlink fix works; second panic found and fixed

**Symptom:** On the physical device (Samsung, Android 14, mobile data) the app
closed when tapping Start node. The capture in `crash.txt` shows:

- `PocCamera: start requested` → `GoLog: tsembed: start requested` → `start succeeded`:
  the patched stdlib fallback enumerated the real interfaces
  (`rmnet0:[10.196.153.46/24 ...] rmnet2:[...] v4=true v6=true`) and `Start()`
  returned cleanly. **The CL 507415 netlink workaround works on-device.**
- Then `GoLog: panic: no safe place found to store log state` →
  `Fatal signal 6 (SIGABRT)`, killing the process (gomobile does not convert Go
  panics into Java exceptions).

**Root cause:** `tailscale.com/logpolicy.LogsDir` (`logpolicy/logpolicy.go:210`)
is called by `ipnlocal/local.go:648` (sockstat logger) during `tsnet.Start()`.
On Android none of its candidates apply: no `$TS_LOGS_DIR`, no `$HOME`/cache dir,
cwd is `/`, and `os.MkdirTemp` fails because `/tmp` does not exist → it panics at
`logpolicy.go:282`.

**Fix:** `tsembed.Start` creates `<stateDir>/logs` and sets `TS_LOGS_DIR` before
starting the node (`tsembed.go`). `LogsDir` honors `TS_LOGS_DIR` first, so the
panic is avoided. Verified in the AAR/APK (`TS_LOGS_DIR` present in
`libgojni.so`).

**Next step:** reinstall `app-debug.apk`, tap Start, and confirm the node reaches
`Authenticating` (interactive URL) and then `Running` in the tailnet.

### 2026-08-10 — Phase 1: netlinkrib experiment APK built with isolated patched toolchain

**Decision:** Build the Phase 1 device-test variant with an isolated Go 1.26.5
toolchain carrying the upstream Android netlink fallback (CL 507415), used only
when the build is invoked explicitly with `-PgoToolchainRoot=...` (or
`PATCHED_GOROOT`). No binaries are committed; the diff is versioned.

**Result:**
- The toolchain was reproduced offline from the official `go1.26.5` module
  source, patched, and rebuilt via `make.bash` (procedure in
  `docs/TOOLCHAIN_PATCH.md`).
- `./gradlew -PgoToolchainRoot=/workspace/go-patched :app:assembleDebug`
  produces `app-debug.apk` whose `arm64-v8a/libgojni.so` contains
  `net.interfaceTableAndroid` / `net.nameToMTU` / `net.indexToName` and no
  longer calls `syscall.NetlinkRIB` on the Android path (verified with
  `go tool nm`).
- The normal build path is unchanged and auditable: `build-android.sh` logs
  `Using patched Go toolchain: ...` only when the variable is set.

**Known limitation (upstream):** the fallback loses MAC addresses and has not
been validated by upstream against Android 14 / targetSdk 34. This experiment
only checks whether `tsnet.Start()` can reach `Running` on the physical device.

**Next step:** install `app-debug.apk` on the physical device and confirm the
node enrolls and reaches `Running` outside the home LAN. If it does not, the
fallback is documented as a failed experiment and the remaining options are an
upstream-merged fix, `wlynxg/anet`, or a `VpnService`-style architecture.

### 2026-08-10 — Phase 1: Android embedding build validated (partial)

**Decision:** Build the embedded Tailscale component with `gomobile bind` from a
Go module wrapping `tsnet`, exposing only `start`/`stop`/`status`.

**Result:** The AAR builds cleanly with
`-target=android/arm64,android/amd64 -androidapi 26 -javapkg com.homelab.poc`.
The generated `com.homelab.poc.tsembed.Tsembed` binding (start/status/stop) and
`libgojni.so` for both ABIs are packaged into `app-debug.apk` (approx. 78 MB
debug). `assembleDebug`, `test` and `lint` pass. RISK-001 (packaging Go code for
Android) is resolved at the build level.

**Notes:**
- The Kotlin bridge (`TailscaleBridge`) is the only component that knows the
  generated binding; the UI consumes `core/connectivity` states.
- Go toolchain 1.26.5+ is required by `tailscale.com` v1.102.2 and auto-downloads
  via the `go.mod` `toolchain` directive.
- Enrollment uses the interactive `login.tailscale.com` URL flow (no auth key in
  the APK), shown in the app UI when the node is `Authenticating`.

**Remaining Phase 1 evidence (device-only):** the node appears in the tailnet as
`poc-camera` and stays `Running` on a physical Android device outside the home
LAN, without the official Tailscale app.

### 2026-08-10 — Phase 1: device test blocked by Go stdlib on Android 11+

**Symptom:** On a physical Android device, `tsnet.Start()` fails with
`tsnet: route ip+net: netlinkrib: permission denied`. The embedded node never
reaches `Running`.

**Root cause:** The `netlinkrib:` prefix comes from the Go standard library, not
from Tailscale's `netlinkrib` package. `net.Interfaces()` calls
`syscall.NetlinkRIB(RTM_GETLINK, AF_UNSPEC)` in `src/net/interface_linux.go`
(`interfaceTable`); on failure that path returns
`os.NewSyscallError("netlinkrib", err)`. Android 11+ (SDK 30) SELinux policy for
`untrusted_app` denies `bind()` on NETLINK sockets and `RTM_GETLINK`, so the
syscall returns `EPERM`. The Go 1.26.5 toolchain required by `tailscale.com`
v1.102.2 has no fallback for this.

**Upstream status:**
- `tailscale/tailscale#17311` (tsnet on gomobile/Android, same error): OPEN.
- `golang/go#40569` (`net.InterfaceAddrs()` fails on Android SDK 30): OPEN since
  Aug 2020.
- Go Gerrit CL 507415 (x/mobile netlink fallback for Android): NEW, never
  merged; last touched 2024-03-04. Partial fix (`Interfaces()` loses MAC
  addresses; `InterfaceAddrs()` resolved).
- Mirror PR `golang/go#61089`: confirmed OPEN (never merged). The author stated
  the Go team would likely reject it (fix may break on later Android versions)
  and recommends `github.com/wlynxg/anet` or a self-built Go toolchain. One user
  reported the fix does not fully work on Android 14 / targetSdk 34
  (`anet.Interfaces()` returned an empty list on a Pixel 7a).

**Tailscale v1.102.2 detail:** `netmon` exposes
`netmon.RegisterInterfaceGetter` (used by the official Android client, which
patches around this) but `tsnet` does not register a getter, so `tsnet` hits the
broken stdlib path via `netmon/state.go` / `netmon/interfaces.go`.

**Affected versions:** Android 11+ / SDK 30; Go stdlib through 1.26.5;
`tailscale.com` v1.102.2 with `tsnet`.

**Decision/status:** The POC is blocked at Phase 1 device validation. No patch
was applied. Preferred options, in order:
1. Upgrade Go/Tailscale once upstream ships a merged fix.
2. Use an official Go toolchain build that works on Android and is supported by
   the Tailscale dependency.
3. Validate the hypothesis with an isolated upstream Go stdlib patch (CL 507415)
   applied to the local toolchain only.
4. Only if the above fail, reconsider the architecture (e.g. the official
   Tailscale Android client path or `VpnService`).

**Notes:** The `tsembed` wrapper lifecycle was hardened so a failed `Start()`
returns to `Stopped` with a surfaced error and allows a retry (see
`native/tailscale/go/tsembed_test.go`). This change is unrelated to the
netlinkrib blocker and applies regardless of the chosen fix.

### 2026-08-08 — POC scope

**Decision:** The POC will validate embedded Tailscale connectivity plus Frigate live video on modern Android.

**Expected result:** Validate remote access to home cameras.

**Excluded:** Nextcloud, Immich, events, notifications, SSO, and production provisioning.

### Android Tailscale embedding approach

**Status:** Build path validated (`gomobile bind` + `tsnet` AAR); device test pending.

**Chosen:** `tsnet` embedded through a Go/Android bridge (`native/tailscale/go`,
`gomobile bind`). `VpnService` remains the fallback only if application-scoped
transport is insufficient for media playback.

### Pending — Live transport

**Status:** Not selected.

**Candidates:** Determined from the actual Frigate/go2rtc configuration and native Android playback feasibility.

## 9. References to Verify During Implementation

Use current primary documentation during implementation because these components evolve quickly:

- Tailscale `tsnet` documentation
- Tailscale source/package documentation
- Android `VpnService` documentation
- Frigate live-view documentation
- Frigate go2rtc configuration/restream documentation
- Frigate API documentation

Do not copy architecture assumptions from old blog posts or random sample repositories without verifying them against current upstream behavior.

## 10. Final Result Template

Complete this section at the end of the POC.

### Result

**GO / REWORK / NO-GO:** TBD

### Embedded Tailscale

TBD

### Android networking architecture

TBD

### Frigate live transport

TBD

### Remote playback result

TBD

### Security observations

TBD

### Production blockers

TBD

### Recommended MVP architecture

TBD
