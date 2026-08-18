# Hearthlane — Development Plan

## Current Product Direction

Hearthlane is a private platform for accessing personal data and resources hosted on infrastructure controlled by the user.

The homelab is enabling infrastructure, not the product.

The central proposition is:

> **My personal data stays in my infrastructure, but I can access it from anywhere, privately.**

Hearthlane should make the underlying infrastructure as invisible as possible.

The initial connectivity layer is Tailscale. It provides private connectivity without requiring users to expose services publicly or understand VPN/NAT/networking details.

See `docs/PROJECT.md` for the full product vision and boundary.

## Current Development Priority — Frigate

The next development cycle is focused on **completing the Frigate integration**.

Target flow:

```text
Home
  |
Cameras
  |
Camera
  +-- Live
  |
  +-- Recent events
         |
       Event
         |
      Playback
```

The complete experience must work on the local network and remotely through Tailscale.

### Phase 1 — Current-state mapping

Before implementation:

- inspect the existing Frigate integration;
- identify current camera loading;
- identify current live-view flow;
- identify LOCAL/Tailscale endpoint resolution;
- identify existing networking/controllers/repositories/models;
- identify current Frigate API usage;
- identify existing playback mechanisms;
- identify the minimum additional Frigate APIs required.

Deliverable: a concrete gap list based on the current implementation.

### Phase 2 — Event model

Define an internal Hearthlane camera-event model rather than blindly mirroring Frigate responses.

Candidate information:

- event ID;
- camera;
- start time;
- end time;
- duration;
- thumbnail;
- detected objects;
- playback information.

### Phase 3 — Frigate adapter/API

Implement only the required integration surface:

- recent events;
- thumbnails;
- event metadata;
- playback information;
- pagination/time windows where required.

Handle:

- events still being processed;
- missing events;
- expired recordings;
- Frigate unavailable;
- malformed/unexpected responses.

Do not create a generic service-discovery framework.

### Phase 4 — Event UI

Implement:

- recent-event list;
- event thumbnail;
- camera;
- timestamp;
- duration;
- detected object information;
- navigation to event detail.

Start with the smallest useful filtering experience.

### Phase 5 — Event playback

Validate:

- short events;
- long events;
- newly created events;
- expired events;
- unavailable camera;
- unavailable Frigate;
- connection loss during playback;
- LOCAL connectivity;
- Tailscale connectivity;
- Wi-Fi -> mobile-data transition;
- mobile-data -> Wi-Fi transition.

### Phase 6 — Error and empty states

Distinguish:

- no recent events;
- Frigate unavailable;
- Tailscale unavailable;
- camera unavailable;
- event missing;
- event expired;
- playback unavailable;
- playback failed.

### Phase 7 — Validation

Before considering the integration complete:

- unit tests;
- integration/API tests where practical;
- UI/navigation tests where practical;
- `./gradlew test lint :app:assembleDebug`;
- Go tests;
- `go vet`;
- physical smoke test.

Acceptance criterion:

> Open Hearthlane -> Cameras -> select a camera -> view recent events -> select an event -> play the recording -> return and continue browsing events.

The complete flow must work locally and remotely.

## Roadmap After Frigate

### Settings

Organize settings around application configuration, connectivity state, useful diagnostics, privacy information, and future integrations.

Do not turn Settings into a homelab administration panel.

### Updates

Provide a clear way to detect a new Hearthlane version, notify the user, expose release information, and guide the user to the appropriate update mechanism.

### Family Location

Implement private family/device location sharing.

Initial scope:

- authorized members/devices;
- map;
- current location;
- background updates;
- privacy and permission controls;
- private connectivity.

Potential later scope:

- location history;
- presence;
- notifications.

### Personal Data

Evaluate focused experiences for:

- documents;
- photos;
- videos;
- files.

Integrate existing self-hosted platforms through adapters when appropriate.

## Explicitly Deferred

### Generic service discovery

Not a current product requirement. Revisit only if a concrete user-facing need demonstrates its value.

### Generic homelab dashboard

Out of scope.

### Infrastructure administration

Out of scope unless a future capability directly supports a Hearthlane personal-data experience.

## Decision Criteria

For new features and architecture proposals, ask:

> **Does this functionality increase the user's control over their own data or provide a meaningful private experience, without requiring the user to understand the infrastructure behind it?**

Strong candidates should serve a personal use case, preserve private access, hide infrastructure complexity, integrate with existing self-hosted solutions where useful, and avoid unnecessary generic abstractions.

## Decision Log

### Project direction: Hearthlane is a private personal-data platform

**Decision**

Hearthlane is defined as a private platform for accessing personal data and resources hosted on infrastructure controlled by the user.

It is not conceptually defined as a "homelab gateway".

The homelab is enabling infrastructure, not the product.

**Rationale**

The initial Tailscale integration demonstrated that remote access to private resources can be provided without exposing services directly to the public Internet.

The project should use that capability to build user-facing experiences around personal data and everyday life rather than infrastructure administration.

**Initial product areas**

- Family location sharing
- Home cameras
- Recent Frigate events and playback
- Future access to documents, photos, videos, and other personal data

**Explicit boundary**

Hearthlane should not become a generic homelab administration dashboard, service launcher, or service discovery interface.

Existing self-hosted applications may be integrated through adapters, but the underlying application should remain an implementation detail whenever possible.

**Service discovery**

Automatic discovery of services is not considered a core product feature at this stage.

It may be revisited as an implementation or configuration mechanism, but it should not define the user experience.

**Guiding principle**

> Does this functionality increase the user's control over their own data or provide a meaningful private experience, without requiring the user to understand the infrastructure behind it?

---

> **Important:** The existing historical Decision Log entries in this file should remain unchanged below this point. The current project should append new decisions rather than rewriting historical records.
