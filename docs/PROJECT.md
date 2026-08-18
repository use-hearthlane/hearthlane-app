# Hearthlane — Project Vision

**Your private way home.**

## 1. Purpose

Hearthlane is a private platform for accessing personal data and services, from anywhere, through infrastructure controlled by the user.

The goal is not to build another homelab dashboard.

The goal is to make self-hosting practical for everyday personal use: the user's location, cameras, documents, photos, videos, and other personal resources should remain under the user's control while still being conveniently accessible when away from home.

Hearthlane provides the user-facing experience. The underlying infrastructure remains an implementation detail whenever possible.

## 2. The Core Idea

Hearthlane follows a simple principle:

> **Give users convenient access to their own data without requiring them to expose their private services to the public Internet or understand the infrastructure behind them.**

Self-hosting is the means, not the product.

A homelab is one possible form of the infrastructure supporting Hearthlane, but Hearthlane should not be conceptually limited to homelab enthusiasts.

The infrastructure may eventually be a NAS, home server, mini PC, private server, VPS, or another environment controlled by the user.

## 3. Privacy Model

Hearthlane is built around the idea that personal data should remain under the user's control.

The preferred architecture is:

```text
                    Hearthlane
                         |
              Private connectivity
                         |
        +----------------+----------------+
        |                |                |
   Location          Cameras          Personal data
        |                |                |
    Self-hosted       Frigate          NAS / services
```

Services should not need to be exposed directly to the public Internet merely to make them accessible remotely.

The initial implementation uses Tailscale as the private connectivity layer. This was an intentional strategic choice: it allows Hearthlane to provide remote access without making networking, NAT traversal, VPN configuration, port forwarding, or public exposure part of the normal user experience.

Tailscale is therefore infrastructure, not the product itself.

The architecture should remain sufficiently abstract that the connectivity layer can evolve independently of the Hearthlane experience.

## 4. User Experiences

Hearthlane should focus on experiences that are directly meaningful to the user's personal life.

### 4.1 Family and Location

Hearthlane should allow authorized people to share and view their locations privately.

Initial direction:

* share location between family members;
* display authorized devices;
* operate over the private connectivity layer;
* avoid requiring users to understand the underlying network.

Future possibilities may include:

* location history;
* device presence;
* notifications;
* family-oriented location features.

### 4.2 Home and Cameras

Hearthlane should provide convenient access to the user's home cameras.

The initial implementation integrates with Frigate.

The intended experience includes:

* live camera view;
* camera navigation;
* recent events;
* event playback;
* access while away from the local network.

The goal is not to reproduce the entire Frigate administration interface.

Hearthlane should expose the parts of the camera system that are useful to an ordinary user.

### 4.3 Personal Data

Future Hearthlane experiences may provide access to:

* documents;
* photos;
* videos;
* files;
* other personal data hosted by the user's infrastructure.

These should be incorporated as user experiences rather than presented simply as links to internal services.

For example, if photos are provided by a self-hosted photo platform, the user should primarily experience **Photos**, not the underlying service name.

## 5. Service Integrations

Hearthlane may integrate with existing self-hosted applications, but integrations should not define the product.

The preferred model is:

```text
Hearthlane experience
        |
   adapter/integration
        |
 underlying self-hosted service
```

For example:

```text
Cameras
   |
 Frigate

Photos
   |
 Immich

Documents
   |
 Nextcloud
```

The underlying service is an implementation detail whenever possible.

This allows Hearthlane to provide a consistent experience even when the user's infrastructure changes.

## 6. What Hearthlane Is Not

Hearthlane is deliberately **not** intended to become a generic homelab administration dashboard.

The project should avoid becoming primarily:

* a Proxmox dashboard;
* a Docker management interface;
* a Portainer replacement;
* a network administration interface;
* a monitoring dashboard;
* a generic service launcher;
* a collection of links to internal applications.

The fact that a service exists in the user's infrastructure does not by itself make it a Hearthlane feature.

A feature should have a clear relationship to the user's personal data, privacy, family, home, or personal digital life.

## 7. Service Discovery

Automatic discovery of services is intentionally not a core product requirement at this stage.

Discovery may eventually be useful internally for configuration or integration, but Hearthlane should not make "discover everything running in my homelab" a central user experience.

The important question is:

> **What personal resource does the user want to access?**

not:

> **What services are running on the network?**

This distinction prevents Hearthlane from becoming a homelab dashboard.

## 8. Product Boundary

A useful test for evaluating new features is:

> **Does this functionality increase the user's control over their own data or provide a meaningful private experience, without requiring the user to understand the infrastructure behind it?**

If yes, it is a strong candidate for Hearthlane.

If the primary justification is simply that it makes homelab administration easier, it is probably outside the core product.

## 9. Architecture Principles

### User experience over infrastructure

Infrastructure should disappear behind the experience whenever possible.

### Private by default

Remote access should not require public exposure of personal services.

### Self-hosted by design

The user's data should remain in infrastructure controlled by the user.

### Integration without dependency on a specific service

Hearthlane should integrate with existing solutions without allowing those solutions to define the product.

### Capability over administration

Hearthlane should expose useful capabilities rather than administrative interfaces.

### Incremental expansion

The project should begin with a small number of excellent experiences and expand only when new capabilities reinforce the central privacy proposition.

## 10. Current Strategic Direction

The initial roadmap is intentionally centered on two concrete experiences:

1. **Family location**
2. **Home cameras and recent Frigate events**

These two areas establish the central Hearthlane proposition:

> **My personal data stays in my infrastructure, but I can access it from anywhere.**

Once that foundation is solid, Hearthlane can expand into personal documents, photos, videos, and other meaningful personal resources.

The project should resist expanding into generic infrastructure management simply because the underlying technology makes it possible.
