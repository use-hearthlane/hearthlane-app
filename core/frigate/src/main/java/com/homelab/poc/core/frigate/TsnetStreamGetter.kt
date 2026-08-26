package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpStream
import com.homelab.poc.core.connectivity.HttpStreamGetter

/**
 * [HttpStreamGetter] that dials exclusively through the embedded tsnet
 * network. Used only for the Tailscale path; it never falls back to the
 * Android network.
 */
class TsnetStreamGetter(private val gateway: TsnetGateway) : HttpStreamGetter {

    override suspend fun open(url: String, connectTimeoutMs: Long): HttpStream =
        gateway.httpOpenStream(url, connectTimeoutMs)
}