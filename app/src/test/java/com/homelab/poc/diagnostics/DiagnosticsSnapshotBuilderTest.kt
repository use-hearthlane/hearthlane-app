package com.homelab.poc.diagnostics

import com.homelab.poc.controller.PlaybackSnapshot
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind
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
            transport = TransportKind.TAILSCALE,
            transportSwitchCount = 1,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
        )

        assertEquals("Connected (TAILSCALE)", snapshot.frigateConnectivity)
        assertEquals("connected", snapshot.tailscaleState)
        assertEquals("TAILSCALE", snapshot.transport)
        assertEquals("0.17.1", snapshot.serverVersion)
        assertEquals("0.1.0", snapshot.appVersion)
    }

    @Test
    fun `connected over LOCAL reports the node as disconnected`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = FrigateConnection.Connected(TransportKind.LOCAL, "0.17.1"),
            connecting = false,
            transport = TransportKind.LOCAL,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
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
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
        )

        assertEquals("Failed", snapshot.frigateConnectivity)
        assertEquals("authenticating", snapshot.tailscaleState)
    }

    @Test
    fun `plain failure maps to failed`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = FrigateConnection.Failed("timeout"),
            connecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
        )

        assertEquals("failed", snapshot.tailscaleState)
    }

    @Test
    fun `connecting with no connection yet maps to connecting`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = null,
            connecting = true,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
        )

        assertEquals("connecting", snapshot.frigateConnectivity)
        assertEquals("connecting", snapshot.tailscaleState)
    }

    @Test
    fun `never connected maps to disconnected`() {
        val snapshot = buildDiagnosticsSnapshot(
            connection = null,
            connecting = false,
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(),
            appVersion = "0.1.0",
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
            transport = null,
            transportSwitchCount = 0,
            playback = PlaybackSnapshot(lastError = "session expired: https://login.tailscale.com/a/abc123XYZ"),
            appVersion = "0.1.0",
        )

        val report = DiagnosticsReport.build(snapshot)
        assertFalse(
            "the copied report must never contain the enrollment URL",
            report.contains("login.tailscale.com"),
        )
    }
}
