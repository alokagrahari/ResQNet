package com.resqnet.ble

enum class BleHandshakeState {
    NOT_STARTED,
    WAITING,
    HELLO_SENT,
    ACK_RECEIVED,
    SUCCESS,
    FAILED
}

fun BleHandshakeState.uiLabel(): String = when (this) {
    BleHandshakeState.NOT_STARTED -> "Not started"
    BleHandshakeState.WAITING -> "Waiting"
    BleHandshakeState.HELLO_SENT -> "HELLO sent"
    BleHandshakeState.ACK_RECEIVED -> "ACK received"
    BleHandshakeState.SUCCESS -> "HELLO/ACK SUCCESS"
    BleHandshakeState.FAILED -> "Failed"
}
