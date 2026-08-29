package org.hearthlane.diagnostics

import org.hearthlane.controller.PlaybackSnapshot
import org.hearthlane.core.frigate.FrigateConnection
import org.hearthlane.core.frigate.TransportKind
import org.hearthlane.core.relay.RelayConnection
import org.hearthlane.core.relay.RelayTransportKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticsSnapshotBuilderTest {

    @Test
    fun `connected over TAILSCALE maps connectivity, transport and node state`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = FrigateConnection.Connected(TransportKind.TAILSCALE, "0.17.1"),
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = TransportKind.TAILSCALE,
            transportSwitchCount = 1,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("Connected (TAILSCALE)", snapshot.frigateConnectivity)
        assertEquals("connected", snapshot.tailscaleState)
        assertEquals("TAILSCALE", snapshot.transport)
        assertEquals("0.17.1", snapshot.serverVersion)
        assertEquals("0.1.0", snapshot.appVersion)
        assertEquals("hearthlane-abc12345", snapshot.nodeHostname)
    }

    @Test
    fun `connected over LOCAL reports the node as disconnected`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = TransportKind.LOCAL,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("Connected (LOCAL)", snapshot.frigateConnectivity)
        assertEquals("disconnected", snapshot.tailscaleState)
    }

    @Test
    fun `pending enrollment maps to authenticating`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = FrigateConnection.Failed(
                error = "Tailscale requires authentication",
                authUrl = "https://login.tailscale.com/a/abc123",
                authRequired = true,
            ),
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("Failed", snapshot.frigateConnectivity)
        assertEquals("authenticating", snapshot.tailscaleState)
    }

    @Test
    fun `plain failure maps to failed`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = FrigateConnection.Failed("timeout"),
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("failed", snapshot.tailscaleState)
    }

    @Test
    fun `connecting with no connection yet maps to connecting`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = null,
            connecting = true,
            relayConnection = null,
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("connecting", snapshot.frigateConnectivity)
        assertEquals("connecting", snapshot.tailscaleState)
    }

    @Test
    fun `never connected maps to disconnected`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = null,
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("Disconnected", snapshot.frigateConnectivity)
        assertEquals("disconnected", snapshot.tailscaleState)
        assertNull(snapshot.transport)
        assertNull(snapshot.serverVersion)
    }

    @Test
    fun `playback fields and counters pass through`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = null,
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 2,
            playback = PlaybackSnapshot(
                playbackState = "playing",
                lastError = "session expired",
                firstFrameElapsedMs = 1500,
                errorCount = 3,
                bytesTransferred = 123456,
                recoveryCount = 2,
            ),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals(2, snapshot.transportSwitchCount)
        assertEquals("playing", snapshot.playbackState)
        assertEquals("session expired", snapshot.lastPlaybackError)
        assertEquals(1500L, snapshot.firstFrameElapsedMs)
        assertEquals(3, snapshot.errorCount)
        assertEquals(123456L, snapshot.bytesTransferred)
        assertEquals(2, snapshot.recoveryCount)
    }

    @Test
    fun `report built from the snapshot never contains an auth URL`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = FrigateConnection.Failed(
                error = "enrollment pending: https://login.tailscale.com/a/abc123XYZ",
                authUrl = "https://login.tailscale.com/a/abc123XYZ",
                authRequired = true,
            ),
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(lastError = "session expired: https://login.tailscale.com/a/abc123XYZ"),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        val report = DiagnosticsReport.build(snapshot)
        assertFalse(
            "the copied report must never contain the enrollment URL",
            report.contains("login.tailscale.com"),
        )
    }

    @Test
    fun `relay connectivity maps the relay connection state`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = null,
            connecting = false,
            relayConnection = RelayConnection.Connected(RelayTransportKind.TAILSCALE),
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("Connected (TAILSCALE)", snapshot.relayConnectivity)
    }

    @Test
    fun `relay connecting maps to connecting`() {
        assertEquals(
            "connecting",
            relayConnectivityLabel(connection = null, connecting = true),
        )
    }

    @Test
    fun `relay disconnected maps to disconnected`() {
        assertEquals(
            "Disconnected",
            relayConnectivityLabel(connection = null, connecting = false),
        )
    }

    @Test
    fun `base domain flows into the report`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = null,
            connecting = false,
            relayConnection = null,
            relayConnecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
            nodeHostname = "hearthlane-abc12345",
            baseDomain = "hearthlane.example",
        )

        assertEquals("hearthlane.example", snapshot.baseDomain)
        val report = DiagnosticsReport.build(snapshot)
        assertEquals(true, report.contains("hearthlane.example"))
    }
}
