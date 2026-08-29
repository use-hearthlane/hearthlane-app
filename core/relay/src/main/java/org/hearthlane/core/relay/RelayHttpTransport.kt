package org.hearthlane.core.relay

import org.hearthlane.core.connectivity.HttpBytesResult
import org.hearthlane.core.connectivity.HttpUrlConnectionBytesClient
import org.hearthlane.core.connectivity.TsnetGateway

/**
 * The HTTP primitive a relay transport or client uses to reach the relay.
 * Implementations never fall back to another network path: the local
 * implementation talks over the normal Android network, the tsnet one
 * exclusively through the embedded tunnel. This mirrors the project pattern
 * of selecting a getter by kind without leaking the path to the UI.
 */
fun interface RelayHttpTransport {
    suspend fun request(
        method: String,
        url: String,
        contentType: String?,
        body: String?,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): HttpBytesResult
}

/**
 * Selects the [RelayHttpTransport] for the given transport kind.
 *
 * [RelayTransportKind.TAILSCALE] always resolves to the tsnet transport; the
 * local client is never constructed for that path, so relay data requests can
 * never escape to the Android network when the remote path is active. This
 * mirrors the Frigate bytesGetter selection.
 */
fun relayHttpTransportFor(
    transport: RelayTransportKind,
    gateway: TsnetGateway,
): RelayHttpTransport = when (transport) {
    RelayTransportKind.LOCAL -> localRelayHttpTransport()
    RelayTransportKind.TAILSCALE -> RelayHttpTransport(gateway::httpRequest)
}

/** LAN [RelayHttpTransport] over the normal Android network. */
fun localRelayHttpTransport(): RelayHttpTransport {
    val client = HttpUrlConnectionBytesClient()
    return RelayHttpTransport { method, url, contentType, body, headers, timeoutMs ->
        client.request(method, url, contentType, body, headers, timeoutMs)
    }
}