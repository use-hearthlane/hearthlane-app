## Decision Log — Hearthlane Product Direction

### Project direction: Hearthlane is a private personal-data platform

**Decision**

Hearthlane is no longer conceptually defined as a "homelab gateway".

Its purpose is to provide a private, convenient way for users to access their own personal data and resources from anywhere, using self-hosted infrastructure controlled by the user.

The homelab is an enabling infrastructure, not the product.

**Rationale**

The initial Tailscale integration demonstrated an important capability: remote access to private resources without exposing services directly to the public Internet.

This capability should be used to build user-facing experiences around personal data and everyday life rather than around infrastructure administration.

**Initial product areas**

* Family location sharing
* Home cameras
* Recent Frigate events and playback
* Future access to documents, photos, videos, and other personal data

**Explicit boundary**

Hearthlane should not become a generic homelab administration dashboard, service launcher, or service discovery interface.

Existing self-hosted applications may be integrated through adapters, but the underlying application should remain an implementation detail whenever possible.

For example, users should experience "Cameras", "Photos", and "Documents" rather than being required to interact with Frigate, Immich, Nextcloud, or other infrastructure services directly.

**Service discovery**

Automatic discovery of services is not considered a core product feature at this stage. It may be revisited later as an implementation or configuration mechanism, but it should not define the user experience.

**Guiding principle**

> Does this functionality increase the user's control over their own data or provide a meaningful private experience, without requiring the user to understand the infrastructure behind it?

This decision establishes the product boundary for future roadmap and architecture decisions.
