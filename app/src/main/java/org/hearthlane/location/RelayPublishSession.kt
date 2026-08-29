package org.hearthlane.location

import org.hearthlane.core.connectivity.TsnetGateway
import org.hearthlane.core.relay.HttpRelayClient
import org.hearthlane.core.relay.LocalRelayTransport
import org.hearthlane.core.relay.RelayClient
import org.hearthlane.core.relay.RelayConfig
import org.hearthlane.core.relay.RelayConnection
import org.hearthlane.core.relay.RelayConnectionManager
import org.hearthlane.core.relay.RelayException
import org.hearthlane.core.relay.TailscaleRelayTransport
import org.hearthlane.core.relay.relayHttpTransportFor

/**
 * Relay connectivity for the background publishing path.
 *
 * Probes the relay LOCAL first and falls back to Tailscale — the same
 * transparent strategy as the foreground path — then binds one [RelayClient]
 * to the winning transport. The winning transport is cached so the loop does
 * not re-probe on every cycle; a failed publish invalidates the session (see
 * [BackgroundLocationPublisher.onPublishFailure]) so the next cycle re-probes.
 * This is how a device that left Wi-Fi, or came back to the LAN, recovers
 * without any UI involvement.
 */
internal class RelayPublishSession(
    private val gateway: TsnetGateway,
    private val config: () -> RelayConfig,
    private val connector: suspend (RelayConfig) -> RelayConnection = defaultConnect(gateway),
) {
    private var connection: RelayConnection? = null

    /**
     * Returns a [RelayClient] bound to the currently active transport,
     * connecting LOCal then Tailscale on the first call (or after
     * [invalidate]).
     */
    suspend fun client(): RelayClient {
        val kind = (connection as? RelayConnection.Connected)?.transport ?: connect().transport
        val cfg = config()
        return HttpRelayClient(
            transport = relayHttpTransportFor(kind, gateway),
            baseUrl = cfg.localBaseUrl,
            timeoutMs = cfg.requestTimeoutMs,
        )
    }

    /** Drops the cached transport so the next [client] call re-probes. */
    fun invalidate() {
        connection = null
    }

    private suspend fun connect(): RelayConnection.Connected {
        val cfg = config()
        val result = connector(cfg)
        val connected = result as? RelayConnection.Connected
            ?: throw RelayException((result as RelayConnection.Failed).error)
        connection = connected
        return connected
    }

    private companion object {
        /** Production connector: the proven transparent LOCAL -> Tailscale probe. */
        fun defaultConnect(gateway: TsnetGateway): suspend (RelayConfig) -> RelayConnection = { cfg ->
            RelayConnectionManager(
                config = cfg,
                localTransport = LocalRelayTransport(cfg),
                tailscaleTransport = TailscaleRelayTransport(gateway, cfg),
                tailscaleGateway = gateway,
            ).connect()
        }
    }
}