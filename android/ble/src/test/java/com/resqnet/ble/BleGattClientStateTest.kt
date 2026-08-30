package com.resqnet.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleGattClientStateTest {

    @Test
    fun mapConnectionState_connected() {
        assertEquals(
            BleGattClientState.CONNECTED,
            BleGattClient.mapConnectionState(2)
        )
    }

    @Test
    fun mapConnectionState_connecting() {
        assertEquals(
            BleGattClientState.CONNECTING,
            BleGattClient.mapConnectionState(1)
        )
    }

    @Test
    fun mapConnectionState_disconnected() {
        assertEquals(
            BleGattClientState.DISCONNECTED,
            BleGattClient.mapConnectionState(0)
        )
    }

    @Test
    fun successfulConnect_requiresSuccessAndConnected() {
        assertTrue(BleGattClient.isSuccessfulConnect(0, 2))
        assertFalse(BleGattClient.isSuccessfulConnect(257, 2))
        assertFalse(BleGattClient.isSuccessfulConnect(0, 0))
        assertFalse(BleGattClient.isSuccessfulConnect(0, 1))
    }

    @Test
    fun precheck_alreadyConnecting() {
        assertEquals(
            "GATT client already connecting to this device",
            BleGattClient.precheckConnect(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingConnectPermission = false,
                alreadyConnected = false,
                alreadyConnecting = true
            )
        )
    }

    @Test
    fun precheck_alreadyConnected() {
        assertEquals(
            "GATT client already connected to this device",
            BleGattClient.precheckConnect(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingConnectPermission = false,
                alreadyConnected = true,
                alreadyConnecting = false
            )
        )
    }

    @Test
    fun precheck_missingPermission() {
        assertEquals(
            "BLUETOOTH_CONNECT permission is missing",
            BleGattClient.precheckConnect(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingConnectPermission = true,
                alreadyConnected = false,
                alreadyConnecting = false
            )
        )
    }

    @Test
    fun precheck_ready() {
        assertNull(
            BleGattClient.precheckConnect(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingConnectPermission = false,
                alreadyConnected = false,
                alreadyConnecting = false
            )
        )
    }

    @Test
    fun discovery_success_whenAllAttributesPresent() {
        assertNull(
            BleGattClient.validateDiscoveredAttributes(
                serviceFound = true,
                rxFound = true,
                txFound = true,
                cccdFound = true
            )
        )
    }

    @Test
    fun discovery_missingService() {
        assertEquals(
            "ResQNet service missing",
            BleGattClient.validateDiscoveredAttributes(
                serviceFound = false,
                rxFound = false,
                txFound = false,
                cccdFound = false
            )
        )
    }

    @Test
    fun discovery_missingRx() {
        assertEquals(
            "RX characteristic missing",
            BleGattClient.validateDiscoveredAttributes(
                serviceFound = true,
                rxFound = false,
                txFound = true,
                cccdFound = true
            )
        )
    }

    @Test
    fun discovery_missingTx() {
        assertEquals(
            "TX characteristic missing",
            BleGattClient.validateDiscoveredAttributes(
                serviceFound = true,
                rxFound = true,
                txFound = false,
                cccdFound = false
            )
        )
    }

    @Test
    fun discovery_missingCccd() {
        assertEquals(
            "notification setup failure: CCCD missing",
            BleGattClient.validateDiscoveredAttributes(
                serviceFound = true,
                rxFound = true,
                txFound = true,
                cccdFound = false
            )
        )
    }

    @Test
    fun clientState_includesDiscoveryAndNotifications() {
        assertEquals(
            listOf(
                BleGattClientState.DISCONNECTED,
                BleGattClientState.CONNECTING,
                BleGattClientState.CONNECTED,
                BleGattClientState.SERVICES_DISCOVERED,
                BleGattClientState.NOTIFICATIONS_ENABLED
            ),
            BleGattClientState.entries.toList()
        )
    }
}
