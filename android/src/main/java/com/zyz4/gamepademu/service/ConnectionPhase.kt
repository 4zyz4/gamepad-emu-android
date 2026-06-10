package com.zyz4.gamepademu.service

enum class ConnectionPhase {
    IDLE,
    REQUESTING_PERMISSIONS,
    REGISTERING_PROFILE,
    RECONNECTING,
    LISTENING,
    DISCOVERABLE,
    PAIRING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

enum class ConnectionError {
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED,
    REGISTRATION_FAILED,
    CONNECTION_LOST,
}
