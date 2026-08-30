package com.resqnet.ble

import java.util.UUID

object BleConstants {

    val SERVICE_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789abc")

    val MESSAGE_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789abd")

    val RESPONSE_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("12345678-1234-1234-1234-123456789abe")

    /** Client writes payload to the server (WRITE / WRITE_NO_RESPONSE). */
    val RX_CHARACTERISTIC_UUID: UUID = MESSAGE_CHARACTERISTIC_UUID

    /** Server notifies payload to the client (NOTIFY). */
    val TX_CHARACTERISTIC_UUID: UUID = RESPONSE_CHARACTERISTIC_UUID

    /** Bluetooth SIG Client Characteristic Configuration Descriptor. */
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val HELLO_MESSAGE = "HELLO"
    const val ACK_MESSAGE = "ACK"
}
