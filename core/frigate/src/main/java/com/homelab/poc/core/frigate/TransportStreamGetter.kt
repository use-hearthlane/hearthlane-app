package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpStreamGetter

/**
 * Selects the [HttpStreamGetter] to use for the given [TransportKind].
 *
 * [TransportKind.TAILSCALE] always resolves to the tsnet stream getter; the
 * local getter is never constructed for that path, so progressive playback
 * requests can never escape to the Android network. Mirrors [bytesGetterFor].
 */
fun streamGetterFor(
    transport: TransportKind,
    gateway: TsnetGateway,
): HttpStreamGetter = when (transport) {
    TransportKind.LOCAL -> HttpUrlConnectionStreamGetter()
    TransportKind.TAILSCALE -> TsnetStreamGetter(gateway)
}