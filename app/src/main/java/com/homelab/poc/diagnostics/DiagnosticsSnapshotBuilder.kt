package com.homelab.poc.diagnostics

import com.homelab.poc.controller.PlaybackSnapshot
import com.homelab.poc.core.frigate.FrigateConnection
import com.homelab.poc.core.frigate.TransportKind

/**
 * Builds the allow-listed diagnostics [DiagnosticsReport.Snapshot] from the
 * shared app state. Pure so the V1.5 Diagnostics screen is unit-testable
 * without Compose or networking. The sanitization guarantee stays in
 * [DiagnosticsReport.build]; this only selects which fields the report may
 * ever contain (an auth URL or token is not part of the input shape).
 */
fun buildDiagnosticsSnapshot(
    connection: FrigateConnection?,
    connecting: Boolean,
    transport: TransportKind?,
    transportSwitchCount: Int,
    playback: PlaybackSnapshot,
    appVersion: String,
): DiagnosticsReport.Snapshot {
    val connected = connection as? FrigateConnection.Connected
    return DiagnosticsReport.Snapshot(
        appVersion = appVersion,
        frigateConnectivity = frigateConnectivityLabel(connection, connecting),
        tailscaleState = tailscaleStateLabel(connection, connecting),
        transport = transport?.name,
        transportSwitchCount = transportSwitchCount,
        playbackState = playback.playbackState,
        lastPlaybackError = playback.lastError,
        firstFrameElapsedMs = playback.firstFrameElapsedMs,
        serverVersion = connected?.version,
        errorCount = playback.errorCount,
        bytesTransferred = playback.bytesTransferred,
        recoveryCount = playback.recoveryCount,
    )
}

/** Frigate connectivity label for the Diagnostics report. */
fun frigateConnectivityLabel(
    connection: FrigateConnection?,
    connecting: Boolean,
): String = when {
    connecting && connection == null -> "connecting"
    connection is FrigateConnection.Connected -> "Connected (${connection.transport.name})"
    connection is FrigateConnection.Failed -> "Failed"
    else -> "Disconnected"
}

/**
 * Tailscale node state label derived from the app-facing connection state.
 * LOCAL means the embedded node is stopped (disconnected); TAILSCALE means it
 * is up; a pending enrollment is "authenticating".
 */
fun tailscaleStateLabel(
    connection: FrigateConnection?,
    connecting: Boolean,
): String = when {
    connecting -> "connecting"
    connection is FrigateConnection.Connected ->
        if (connection.transport == TransportKind.TAILSCALE) "connected" else "disconnected"
    (connection as? FrigateConnection.Failed)?.authRequired == true -> "authenticating"
    connection is FrigateConnection.Failed -> "failed"
    else -> "disconnected"
}
