package com.homelab.poc.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private val snapshot = DiagnosticsReport.Snapshot(
        appVersion = "0.1.0",
        frigateConnectivity = "Connected (TAILSCALE)",
        tailscaleState = "connected",
        transport = "TAILSCALE",
        transportSwitchCount = 2,
        playbackState = "playing",
        lastPlaybackError = null,
        firstFrameElapsedMs = 1500,
        serverVersion = "0.17.1-416a9b7",
        errorCount = 3,
        bytesTransferred = 123456,
        recoveryCount = 1,
    )

    @Test
    fun `report contains every allow-listed field`() {
        val report = DiagnosticsReport.build(snapshot)

        assertTrue(report.contains("App version: 0.1.0"))
        assertTrue(report.contains("Frigate connectivity: Connected (TAILSCALE)"))
        assertTrue(report.contains("Tailscale state: connected"))
        assertTrue(report.contains("Selected transport: TAILSCALE"))
        assertTrue(report.contains("Transport switches: 2"))
        assertTrue(report.contains("Playback state: playing"))
        assertTrue(report.contains("Last playback error: none"))
        assertTrue(report.contains("Time to first frame: 1500 ms"))
        assertTrue(report.contains("Server version: 0.17.1-416a9b7"))
        assertTrue(report.contains("Diagnostics: errors 3, bytes 123456, recoveries 1"))
    }

    @Test
    fun `auth URL embedded in a playback error is redacted`() {
        val report = DiagnosticsReport.build(
            snapshot.copy(lastPlaybackError = "session expired: https://login.tailscale.com/a/abc123XYZ"),
        )

        assertFalse("the report must never contain the enrollment URL", report.contains("login.tailscale.com"))
        assertTrue(report.contains("[redacted]"))
    }

    @Test
    fun `control-plane URL is redacted`() {
        val report = DiagnosticsReport.build(
            snapshot.copy(lastPlaybackError = "backend unreachable: https://controlplane.tailscale.com/admin/xyz"),
        )

        assertFalse("the report must never contain a tailscale control URL", report.contains("controlplane.tailscale.com"))
    }

    @Test
    fun `token-shaped strings are redacted`() {
        val token = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val report = DiagnosticsReport.build(
            snapshot.copy(lastPlaybackError = "rejected key $token"),
        )

        assertFalse("the report must never contain token-shaped strings", report.contains(token))
    }

    @Test
    fun `short values are not over-redacted`() {
        val report = DiagnosticsReport.build(snapshot)

        assertTrue("short technical values must survive", report.contains("0.17.1-416a9b7"))
        assertTrue("short technical values must survive", report.contains("123456"))
    }

    @Test
    fun `sanitize handles mixed content`() {
        val cleaned = DiagnosticsReport.sanitize(
            "warn: https://login.tailscale.com/a/abc; token=AAAABBBBCCCCDDDDEEEEFFFF; fine",
        )

        assertFalse(cleaned.contains("login.tailscale.com"))
        assertFalse(cleaned.contains("AAAABBBBCCCCDDDDEEEEFFFF"))
        assertTrue(cleaned.contains("fine"))
    }

    @Test
    fun `sanitize is accessible as public API for clipboard sanitization`() {
        // Validates that DiagnosticsReport.sanitize can be called from any
        // module (SetupScreen uses it for clipboard copy).
        val raw = "error: Tailscale requires authentication https://login.tailscale.com/a/secret123"
        val sanitized = DiagnosticsReport.sanitize(raw)

        assertFalse(
            "sanitize must remove the auth URL",
            sanitized.contains("login.tailscale.com"),
        )
        assertTrue(
            "non-sensitive content must survive sanitization",
            sanitized.contains("Tailscale requires authentication"),
        )
    }
}
