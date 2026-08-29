package org.hearthlane.core.connectivity

enum class ConnectivityState {
    DISCONNECTED,
    AUTHENTICATING,
    CONNECTING,
    CONNECTED,
    FAILED,
    STOPPED,
}
