package com.resqnet.ble

enum class BleGattClientState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SERVICES_DISCOVERED,
    NOTIFICATIONS_ENABLED
}
