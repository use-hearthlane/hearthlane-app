# Hearthlane

**Your private way home.**

Hearthlane is an open-source private platform for accessing personal data and resources hosted on infrastructure controlled by the user.

It is not a homelab management dashboard.

The homelab, home server, NAS, or other self-hosted infrastructure is the means that keeps the user's data under their control. Hearthlane is the user-facing experience that makes those resources conveniently accessible from anywhere without requiring private services to be exposed directly to the public Internet.

## Project vision

Hearthlane aims to make self-hosting practical for everyday personal use.

The central idea is:

> **My personal data stays in my infrastructure, but I can access it from anywhere, privately.**

The project focuses on experiences that matter to the user's personal life:

- family location;
- home cameras and security events;
- documents;
- photos;
- videos;
- other personal data hosted by the user's infrastructure.

The infrastructure behind those experiences should remain as invisible as possible.

## Privacy first

Hearthlane is designed around private access to user-controlled infrastructure.

The initial connectivity layer uses Tailscale. This was a strategic choice to provide transparent private connectivity without requiring users to manage port forwarding, public endpoints, NAT traversal, VPN configuration, or other networking details.

Tailscale is infrastructure, not the product.

## User experiences

### Family and location

Hearthlane will provide private location sharing between authorized family members and devices.

### Home cameras

Hearthlane integrates with Frigate to provide a user-oriented camera experience.

The current direction is:

```text
Cameras
  |
  +-- Live view
  |
  +-- Recent events
       |
       +-- Event details
       |
       +-- Playback
```

The goal is not to reproduce the Frigate administration interface.

### Personal data

Future experiences may provide access to:

- documents;
- photos;
- videos;
- files;
- other personal resources.

The user should experience the capability rather than the underlying service whenever possible.

## Integration philosophy

Hearthlane can integrate with existing self-hosted applications through focused adapters.

```text
Hearthlane experience
        |
    adapter
        |
self-hosted service
```

Examples:

```text
Cameras   -> Frigate
Photos    -> Immich
Documents -> Nextcloud
```

The underlying service should not define the user experience.

## What Hearthlane is not

Hearthlane is intentionally not becoming a generic homelab administration platform.

The project should avoid becoming:

- a Proxmox dashboard;
- a Docker management interface;
- a Portainer replacement;
- a network administration interface;
- a monitoring dashboard;
- a generic service launcher;
- a service discovery dashboard.

The fact that something runs in the user's infrastructure does not automatically make it a Hearthlane feature.

## Service discovery

Automatic service discovery is not a core requirement at this stage.

It may eventually be useful as an internal configuration mechanism, but Hearthlane should not make "discover everything running in my homelab" a central user experience.

The important question is:

> **What personal resource does the user want to access?**

not:

> **What services are running on the network?**

## Current development priority

The immediate objective is to **finish the Frigate integration**.

The target experience is:

```text
Home
  |
Cameras
  |
Select camera
  +-- Live
  |
  +-- Recent events
         |
      Select event
         |
      Playback
```

This must work transparently both on the local network and remotely through the private connectivity layer.

## Roadmap

1. **Frigate integration**
   - recent events;
   - event details;
   - playback;
   - basic filtering;
   - robust error states.

2. **Settings**
   - reorganized settings experience;
   - connection/configuration state;
   - useful diagnostics.

3. **Updates**
   - detect and communicate new versions;
   - provide a clear update path.

4. **Family location**
   - private location sharing;
   - family/device view;
   - background location;
   - privacy and permissions.

5. **Personal data**
   - documents;
   - photos;
   - videos;
   - other useful self-hosted resources.

## Architecture principles

### User experience over infrastructure

Infrastructure should disappear behind the experience whenever possible.

### Private by default

Remote access should not require public exposure of personal services.

### Self-hosted by design

Personal data should remain in infrastructure controlled by the user.

### Integration without product dependency

Hearthlane should integrate with existing services without allowing those services to define the product.

### Capability over administration

Expose useful personal capabilities rather than infrastructure management.

### Incremental expansion

Build a small number of excellent experiences before expanding the product surface.

## Guiding question

> **Does this functionality increase the user's control over their own data or provide a meaningful private experience, without requiring the user to understand the infrastructure behind it?**

If yes, it is a strong Hearthlane candidate.

If the main reason is that it makes homelab administration easier, it is probably outside the core product.

## Development

See the documentation under `docs/` for architecture, planning, release procedures, and project decisions.

## License

Hearthlane is licensed under the [GNU General Public License v3.0 (GPL-3.0-only)](LICENSE).

Third-party software notices for components distributed with the application are available in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
