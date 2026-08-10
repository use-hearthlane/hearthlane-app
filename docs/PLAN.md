# POC Plan

## Status

**Phase:** Phase 1 — Embedded Tailscale Spike (in progress)  
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

### Phase 2 — Frigate HTTP Probe

Goal:

Separate basic Frigate reachability from media complexity.

Tasks:

- add minimal HTTP client abstraction
- call a simple Frigate API endpoint
- show success/failure in the app
- test by private Tailscale hostname/IP
- test outside the LAN

Expected UI:

```text
Home Cameras

Private network: Connected
Frigate: Reachable

[ Test connection ]
```

Exit condition:

Frigate HTTP response is obtained outside the home LAN through the embedded private path.

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
