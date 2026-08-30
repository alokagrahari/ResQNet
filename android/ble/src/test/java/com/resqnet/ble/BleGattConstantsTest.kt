package com.resqnet.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.UUID

class BleGattConstantsTest {

    @Test
    fun serviceUuid_isExistingResqnetUuid() {
        assertEquals(
            UUID.fromString("12345678-1234-1234-1234-123456789abc"),
            BleConstants.SERVICE_UUID
        )
    }

    @Test
    fun rxUuid_reusesExistingMessageCharacteristic() {
        assertSame(
            BleConstants.MESSAGE_CHARACTERISTIC_UUID,
            BleConstants.RX_CHARACTERISTIC_UUID
        )
        assertEquals(
            UUID.fromString("12345678-1234-1234-1234-123456789abd"),
            BleConstants.RX_CHARACTERISTIC_UUID
        )
    }

    @Test
    fun txUuid_reusesExistingResponseCharacteristic() {
        assertSame(
            BleConstants.RESPONSE_CHARACTERISTIC_UUID,
            BleConstants.TX_CHARACTERISTIC_UUID
        )
        assertEquals(
            UUID.fromString("12345678-1234-1234-1234-123456789abe"),
            BleConstants.TX_CHARACTERISTIC_UUID
        )
    }

    @Test
    fun cccdUuid_isBluetoothSigStandard() {
        assertEquals(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BleConstants.CCCD_UUID
        )
    }

    @Test
    fun gattStatus_successAndUnknown() {
        assertEquals("GATT_SUCCESS", BleGattServer.mapGattStatus(0))
        assertEquals("GATT status 99", BleGattServer.mapGattStatus(99))
    }

    @Test
    fun serverPrecheck_alreadyRunning() {
        assertEquals(
            "GATT server already running",
            BleGattServer.precheckServerStart(
                bluetoothUnavailable = true,
                bluetoothDisabled = true,
                missingConnectPermission = true,
                alreadyRunning = true
            )
        )
    }

    @Test
    fun serverPrecheck_bluetoothDisabled() {
        assertEquals(
            "Bluetooth is disabled",
            BleGattServer.precheckServerStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = true,
                missingConnectPermission = false,
                alreadyRunning = false
            )
        )
    }

    @Test
    fun serverPrecheck_ready() {
        assertNull(
            BleGattServer.precheckServerStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingConnectPermission = false,
                alreadyRunning = false
            )
        )
    }
}
