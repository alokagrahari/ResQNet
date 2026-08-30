package com.resqnet.ble

import android.content.Context

/**
 * Proven BLE GATT stack plus BLE-backed [BleTransport] for [com.resqnet.mesh.Packet].
 *
 * GATT path (unchanged):
 * advertising → scan → connect → discovery → CCCD → HELLO/ACK → PING/PONG → DATA
 *
 * Mesh path:
 * Packet → MeshEngine → BleTransport → GATT RX/TX
 */
object BleModule {
    @Volatile
    private var link: BleLink? = null

    fun link(context: Context): BleLink {
        val existing = link
        if (existing != null) {
            return existing
        }
        return synchronized(this) {
            link ?: BleLink(context.applicationContext).also { link = it }
        }
    }
}
