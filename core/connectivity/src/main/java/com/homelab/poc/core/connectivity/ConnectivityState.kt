package com.homelab.poc.core.connectivity

enum class ConnectivityState {
    DISCONNECTED,
    AUTHENTICATING,
    CONNECTING,
    CONNECTED,
    FAILED,
    STOPPED,
}
