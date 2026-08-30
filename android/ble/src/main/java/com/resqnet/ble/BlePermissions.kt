package com.resqnet.ble

import android.Manifest
import android.os.Build

/**
 * Runtime permissions required by the proven BLE stack.
 * Location is requested only below Android 12, matching the prototype.
 */
object BlePermissions {
    fun requiredRuntimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
