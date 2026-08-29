package org.hearthlane.core.connectivity

data class ConnectivityStatus(
    val state: ConnectivityState,
    val authUrl: String? = null,
    val error: String? = null,
)
