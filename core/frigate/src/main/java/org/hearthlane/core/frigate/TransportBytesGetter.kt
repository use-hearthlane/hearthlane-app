package org.hearthlane.core.frigate

import org.hearthlane.core.connectivity.HttpBytesGetter
import org.hearthlane.core.connectivity.HttpUrlConnectionBytesGetter
import org.hearthlane.core.connectivity.TsnetGateway

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
