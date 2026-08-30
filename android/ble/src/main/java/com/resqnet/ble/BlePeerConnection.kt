package com.resqnet.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler

enum class BlePeerRole {
    CLIENT,
    SERVER
}

/**
 * One live ResQNet peer, keyed by MAC. Client and server roles for the same
 * MAC share this object so a second phone never overwrites the first.
 */
class BlePeerConnection(
    val address: String
) {
    @Volatile var device: BluetoothDevice? = null
    @Volatile var gatt: BluetoothGatt? = null
    @Volatile var nodeId: String? = null
    @Volatile var rxCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile var txCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile var connectionState: BleGattClientState = BleGattClientState.DISCONNECTED
    @Volatile var handshakeState: BleHandshakeState = BleHandshakeState.NOT_STARTED
    @Volatile var notificationsEnabled: Boolean = false
    @Volatile var helloAttempted: Boolean = false
    @Volatile var pendingHelloText: String? = null
    @Volatile var pendingPingText: String? = null
    @Volatile var pendingDataText: String? = null
    @Volatile var pendingMeshText: String? = null
    @Volatile var pendingDiscovery: Runnable? = null
    @Volatile var hasClientSession: Boolean = false
    @Volatile var hasServerSession: Boolean = false
    @Volatile var serverSubscribed: Boolean = false
    @Volatile var negotiatedMtu: Int = 23

    fun role(): BlePeerRole {
        return if (hasClientSession && !hasServerSession) {
            BlePeerRole.CLIENT
        } else {
            BlePeerRole.SERVER
        }
    }

    fun isLive(): Boolean = hasClientSession || hasServerSession

    fun isClientActive(): Boolean {
        return hasClientSession &&
            BleHandshakeMachine.isConnectionStillUp(connectionState)
    }

    fun canClientSend(): Boolean {
        return hasClientSession &&
            handshakeState == BleHandshakeState.SUCCESS &&
            gatt != null &&
            rxCharacteristic != null
    }

    fun canServerNotify(): Boolean {
        return hasServerSession && serverSubscribed && device != null
    }

    fun clearClientWriteState() {
        pendingHelloText = null
        pendingPingText = null
        pendingDataText = null
        pendingMeshText = null
        helloAttempted = false
        handshakeState = BleHandshakeMachine.afterDisconnect()
    }

    fun cancelPendingDiscovery(handler: Handler) {
        pendingDiscovery?.let { handler.removeCallbacks(it) }
        pendingDiscovery = null
    }
}
