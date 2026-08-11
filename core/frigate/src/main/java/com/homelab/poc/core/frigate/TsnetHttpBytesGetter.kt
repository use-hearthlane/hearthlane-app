package com.homelab.poc.core.frigate

import com.homelab.poc.core.connectivity.HttpBytesGetter
import com.homelab.poc.core.connectivity.HttpBytesResult

/**
 * [HttpBytesGetter] that dials exclusively through the embedded tsnet network.
 * Used only for the Tailscale path; it never falls back to the Android
 * network.
 */
class TsnetHttpBytesGetter(private val gateway: TsnetGateway) : HttpBytesGetter {

    override suspend fun getBytes(url: String, timeoutMs: Long): HttpBytesResult =
        gateway.httpGetBytes(url, timeoutMs)
}
