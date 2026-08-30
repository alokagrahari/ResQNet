package com.resqnet.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log

/**
 * Owns the proven BLE objects and wires them to [BleTransport] through
 * [BleConnectionManager]. Advertising, scanning, GATT UUIDs, HELLO/ACK, and
 * PING/PONG/DATA are unchanged.
 */
class BleLink(context: Context) {
    companion object {
        private const val TAG = "ResQNetBLE"
    }
    private val appContext = context.applicationContext
    val localNodeId: String = BleNodeId(appContext).getOrCreate()
    val connections = BleConnectionManager()
    val scanner = BleScanner(appContext)
    val advertiser = BleAdvertiser(appContext)
    val gattServer = BleGattServer(appContext)
    val gattClient = BleGattClient(appContext, connections)

    val transport = BleTransport(
        outbound = object : BleTransport.Outbound {
            override fun sendTo(nodeId: String, encoded: String): Boolean {
                return connections.sendToPeer(nodeId, encoded)
            }
        },
        connections = connections,
        adapterLog = { line -> Log.i(TAG, line) }
    )

    init {
        connections.clientSend = { peer, encoded ->
            gattClient.sendPayload(peer, encoded)
        }
        connections.serverSend = { peer, encoded ->
            val device = peer.device
            if (device == null) {
                false
            } else {
                gattServer.sendNotificationTo(
                    device,
                    encoded.toByteArray(Charsets.UTF_8)
                )
            }
        }
        gattServer.onPeerConnected = { device ->
            val address = addressOf(device)
            Log.i(TAG, "BLE PEER CONNECTING: $address")
            val peer = connections.registerPeer(address, BlePeerRole.SERVER)
            peer.device = device
        }
        gattServer.peerNodeIdOf = { device ->
            connections.getPeer(addressOf(device))?.nodeId
        }
        gattServer.onRemoteNodeIdentified = { device, nodeId ->
            registerServerPeer(device, nodeId)
        }
        gattServer.onPeerDisconnected = { device ->
            unregisterServerPeer(device)
        }
        gattServer.onPeerSubscribed = { device ->
            val address = addressOf(device)
            val peer = connections.getPeer(address)
                ?: connections.registerPeer(address, BlePeerRole.SERVER)
            peer.device = device
            peer.hasServerSession = true
            peer.serverSubscribed = true
            attachServerLink(peer)
            val nodeId = peer.nodeId
            if (nodeId != null) {
                Log.i(TAG, "BLE PEER NOTIFICATIONS ENABLED: $nodeId")
            } else {
                Log.i(TAG, "BLE PEER NOTIFICATIONS ENABLED: $address")
            }
        }
        gattServer.onPeerMtuChanged = { device, mtu ->
            connections.updateLiveLink(addressOf(device), mtu = mtu)
        }
        gattServer.onRxWrite = { device, bytes ->
            val text = bytes.toString(Charsets.UTF_8)
            val handshake = BleHandshakeProtocol.parse(text)
            if (handshake is ParsedHandshake.Hello) {
                registerServerPeer(device, handshake.nodeId)
            }
        }
        gattServer.onMeshPayload = { device, text ->
            transport.ingestRaw(text, fromAddress = addressOf(device))
        }
        gattClient.onAckReceived = { address, nodeId ->
            connections.bindNodeId(address, nodeId)
            val peer = connections.getPeer(address)
            if (peer != null) {
                peer.hasClientSession = true
                peer.handshakeState = BleHandshakeState.SUCCESS
                if (peer.gatt != null || peer.rxCharacteristic != null) {
                    connections.logPeerReady(peer)
                }
            }
        }
        gattClient.onMeshPayload = { address, text ->
            transport.ingestRaw(text, fromAddress = address)
        }
        gattClient.onUnhandledMessage = { _, _ ->
            // Unknown non-MESH payloads are not mesh traffic.
        }
        gattClient.onLinkDisconnected = { address ->
            connections.dropClientSession(address)
        }
    }

    fun handleServerDisconnected(device: BluetoothDevice) {
        unregisterServerPeer(device)
    }

    fun handleServerStopped() {
        connections.getConnectedPeers()
            .filter { it.hasServerSession }
            .map { it.address }
            .forEach { connections.dropServerSession(it) }
    }

    private fun registerServerPeer(device: BluetoothDevice, nodeId: String) {
        if (nodeId.isBlank()) {
            return
        }
        val address = addressOf(device)
        val peer = connections.registerPeer(address, BlePeerRole.SERVER)
        peer.device = device
        connections.bindNodeId(address, nodeId)
        attachServerLink(peer)
    }

    private fun attachServerLink(peer: BlePeerConnection) {
        connections.updateLiveLink(
            address = peer.address,
            rx = peer.rxCharacteristic ?: gattServer.localRx(),
            tx = peer.txCharacteristic ?: gattServer.localTx(),
            notificationsEnabled = true,
            mtu = gattServer.mtuFor(peer.address)
        )
    }

    private fun unregisterServerPeer(device: BluetoothDevice) {
        connections.dropServerSession(addressOf(device))
    }

    private fun addressOf(device: BluetoothDevice): String {
        return try {
            device.address
        } catch (_: SecurityException) {
            "unknown"
        }
    }
}
