package com.resqnet.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleErrorMappingTest {

    @Test
    fun scanError_alreadyStarted() {
        assertEquals("Scan already started", BleScanner.mapScanError(1))
    }

    @Test
    fun scanError_applicationRegistrationFailed() {
        assertEquals(
            "BLE scan application registration failed",
            BleScanner.mapScanError(2)
        )
    }

    @Test
    fun scanError_internalError() {
        assertEquals("BLE scan internal error", BleScanner.mapScanError(3))
    }

    @Test
    fun scanError_featureUnsupported() {
        assertEquals(
            "BLE scan feature unsupported on this phone",
            BleScanner.mapScanError(4)
        )
    }

    @Test
    fun scanError_unknownCode_usesFallback() {
        assertEquals(
            "BLE scan failed with error code 99",
            BleScanner.mapScanError(99)
        )
    }

    @Test
    fun advertiseError_dataTooLarge() {
        assertEquals("Advertise data is too large", BleAdvertiser.mapAdvertiseError(1))
    }

    @Test
    fun advertiseError_tooManyAdvertisers() {
        assertEquals(
            "Too many BLE advertisers are already running",
            BleAdvertiser.mapAdvertiseError(2)
        )
    }

    @Test
    fun advertiseError_alreadyStarted() {
        assertEquals(
            "Advertising already started",
            BleAdvertiser.mapAdvertiseError(3)
        )
    }

    @Test
    fun advertiseError_internalError() {
        assertEquals(
            "BLE advertising internal error",
            BleAdvertiser.mapAdvertiseError(4)
        )
    }

    @Test
    fun advertiseError_featureUnsupported() {
        assertEquals(
            "This phone does not support BLE peripheral advertising",
            BleAdvertiser.mapAdvertiseError(5)
        )
    }

    @Test
    fun advertiseError_unknownCode_usesFallback() {
        assertEquals(
            "BLE advertising failed with error code 99",
            BleAdvertiser.mapAdvertiseError(99)
        )
    }

    @Test
    fun scanPrecheck_alreadyRunning_takesPriority() {
        assertEquals(
            "Scan already running",
            BleScanner.precheckScanStart(
                bluetoothUnavailable = true,
                bluetoothDisabled = true,
                missingScanPermission = true,
                scannerUnavailable = true,
                alreadyRunning = true
            )
        )
    }

    @Test
    fun scanPrecheck_bluetoothUnavailable() {
        assertEquals(
            "Bluetooth is unavailable on this device",
            BleScanner.precheckScanStart(
                bluetoothUnavailable = true,
                bluetoothDisabled = false,
                missingScanPermission = false,
                scannerUnavailable = false,
                alreadyRunning = false
            )
        )
    }

    @Test
    fun scanPrecheck_bluetoothDisabled() {
        assertEquals(
            "Bluetooth is disabled",
            BleScanner.precheckScanStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = true,
                missingScanPermission = false,
                scannerUnavailable = false,
                alreadyRunning = false
            )
        )
    }

    @Test
    fun scanPrecheck_missingPermission() {
        assertEquals(
            "BLUETOOTH_SCAN permission is missing",
            BleScanner.precheckScanStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingScanPermission = true,
                scannerUnavailable = false,
                alreadyRunning = false
            )
        )
    }

    @Test
    fun scanPrecheck_scannerUnavailable() {
        assertEquals(
            "BLE scanner is unavailable",
            BleScanner.precheckScanStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingScanPermission = false,
                scannerUnavailable = true,
                alreadyRunning = false
            )
        )
    }

    @Test
    fun scanPrecheck_ready_returnsNull() {
        assertNull(
            BleScanner.precheckScanStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingScanPermission = false,
                scannerUnavailable = false,
                alreadyRunning = false
            )
        )
    }

    @Test
    fun advertisePrecheck_alreadyStarted_takesPriority() {
        assertEquals(
            "Advertising already started",
            BleAdvertiser.precheckAdvertiseStart(
                bluetoothUnavailable = true,
                bluetoothDisabled = true,
                missingAdvertisePermission = true,
                advertiserUnavailable = true,
                peripheralAdvertisingUnsupported = true,
                alreadyStarted = true
            )
        )
    }

    @Test
    fun advertisePrecheck_peripheralUnsupported() {
        assertEquals(
            "This phone does not support BLE peripheral advertising",
            BleAdvertiser.precheckAdvertiseStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingAdvertisePermission = false,
                advertiserUnavailable = false,
                peripheralAdvertisingUnsupported = true,
                alreadyStarted = false
            )
        )
    }

    @Test
    fun advertisePrecheck_advertiserUnavailable() {
        assertEquals(
            "BLE advertiser is unavailable",
            BleAdvertiser.precheckAdvertiseStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingAdvertisePermission = false,
                advertiserUnavailable = true,
                peripheralAdvertisingUnsupported = false,
                alreadyStarted = false
            )
        )
    }

    @Test
    fun advertisePrecheck_ready_returnsNull() {
        assertNull(
            BleAdvertiser.precheckAdvertiseStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingAdvertisePermission = false,
                advertiserUnavailable = false,
                peripheralAdvertisingUnsupported = false,
                alreadyStarted = false
            )
        )
    }
}
