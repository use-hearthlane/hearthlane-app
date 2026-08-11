package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter

/**
 * Selects the [HttpBytesGetter] to use for the given [TransportKind].
 *
 * [TransportKind.TAILSCALE] always resolves to the tsnet getter; the local
 * getter is never constructed for that path, so playback media requests can
 * never escape to the Android network.
 */
fun bytesGetterFor(
    transport: TransportKind,
    gateway: TsnetGateway,
): HttpBytesGetter = when (transport) {
    TransportKind.LOCAL -> HttpUrlConnectionBytesGetter()
    TransportKind.TAILSCALE -> TsnetHttpBytesGetter(gateway)
}
