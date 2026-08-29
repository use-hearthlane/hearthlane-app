# Nginx Proxy Manager — Hearthlane Endpoint Border

The Nginx Proxy Manager (NPM) is the stable border between Hearthlane and the
services of the homelab. Hearthlane never talks to Frigate or the location
relay directly: every request goes to a subdomain served by NPM, which routes
to the internal service.

This document describes what the user must configure in NPM. Hearthlane does
not configure NPM automatically.

## 1. Product architecture

```text
Hearthlane Android
        │
        ├── http://frigate.hearthlane.<base-domain>
        │                         ↓
        │                         NPM
        │                         ↓
        │                       Frigate
        │
        └── http://relay.hearthlane.<base-domain>
                                  ↓
                                  NPM
                                  ↓
                              Location Relay
```

The user configures a single Hearthlane base domain in the app (for example
`hearthlane.omni.corp`). The app derives the two endpoints from it:

- `http://frigate.hearthlane.<base-domain>`
- `http://relay.hearthlane.<base-domain>`

Subdomains are used instead of path prefixes (`/hearthlane/frigate`, ...) on
purpose: Frigate owns many endpoints and streams and can depend on its own
paths, so no path rewriting is introduced.

## 2. DNS

Before NPM can route, the subdomains must resolve. The exact mechanism depends
on the user's environment (a local DNS server, an AdGuard/dnsmasq entry, or a
wildcard DNS record such as `*.hearthlane.<base-domain>` pointing at the NPM
host).

Both Hearthlane transports use the same hostnames:

- On the LAN, the hostnames must resolve to the NPM host.
- On Tailscale, the hostnames must resolve to the NPM host over the tailnet
  (MagicDNS hostname of the NPM machine, or a DNS entry on the tailnet).

Hearthlane never knows IPs, internal ports or the NPM address.

## 3. Transport — HTTP over private connectivity

In this phase Hearthlane uses plain HTTP: the endpoints are only reachable
through private transport (the home LAN or the Tailscale path), never exposed
to the public Internet. No HTTPS is required on the NPM side and no TLS
certificate is configured for these proxy hosts.

Cleartext HTTP is permitted by the app for this reason (see the manifest
comment on `android:usesCleartextTraffic`). This does not disable TLS
validation for HTTPS connections; there is simply no HTTPS in use for the
Hearthlane endpoints in this phase.

## 4. Proxy Host 1 — Frigate

| Field                | Value                                            |
| -------------------- | ------------------------------------------------ |
| Domain names         | `frigate.hearthlane.<base-domain>`               |
| Scheme               | `http`                                           |
| Forward hostname     | internal Frigate host                            |
| Forward port         | Frigate HTTP port (for example 5000)             |
| WebSockets           | enabled (Frigate live view / streams need it)    |
| Block common exploits| user preference                                  |

No path prefix is added. Frigate keeps its native paths, for example:

```text
http://frigate.hearthlane.<base-domain>/api/events
```

arrives at Frigate as `/api/events`.

## 5. Proxy Host 2 — Location Relay

| Field                | Value                                            |
| -------------------- | ------------------------------------------------ |
| Domain names         | `relay.hearthlane.<base-domain>`                 |
| Scheme               | `http`                                           |
| Forward hostname     | internal relay host                              |
| Forward port         | relay port (for example 8080)                    |
| WebSockets           | not required                                     |

No path prefix and no rewrite. The relay contract passes through untouched:

```text
GET  /devices
GET  /devices/{deviceId}/location
PUT  /devices/{deviceId}/location
PUT  /devices/{deviceId}/nickname
```

`http://relay.hearthlane.<base-domain>/devices` reaches the relay as
`/devices` — no `/v1` prefix is ever reintroduced.

## 6. Authentication layers

NPM and Tailscale solve different problems and are not interchangeable:

- NPM routes a hostname to an internal service.
- Tailscale provides private connectivity to the environment.

They do not replace each other. The relay MVP trusts the private connectivity
(LAN/Tailscale) and has no authentication of its own, so no request carries an
`Authorization` header. The complete chain is:

```text
private network (LAN / Tailscale)
    +
HTTP through NPM
```

## 7. Validation checklist

After configuring NPM:

1. `http://frigate.hearthlane.<base-domain>/api/version` returns the Frigate
   version.
2. `http://relay.hearthlane.<base-domain>/devices` responds (no authentication
   required).
3. Live view, events and clips work from the home LAN.
4. The same works through the existing Tailscale path outside the LAN, with
   the UI unaware of which transport is used.