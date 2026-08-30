package com.resqnet.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.resqnet.mesh.Peer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thread-safe multi-peer BLE registry. MeshEngine reads connected nodeIds from
 * here; each entry keeps its own GATT/RX/TX so a second phone cannot replace
 * the first.
 */
class BleConnectionManager {
    companion object {
        private const val TAG = "ResQNetBLE"

        fun idOf(obj: Any?): String {
            return if (obj == null) "null" else Integer.toHexString(System.identityHashCode(obj))
        }
    }

    private val byAddress = ConcurrentHashMap<String, BlePeerConnection>()
    private val recordedSends = CopyOnWriteArrayList<Pair<String, String>>()

    var clientSend: ((BlePeerConnection, String) -> Boolean)? = null
    var serverSend: ((BlePeerConnection, String) -> Boolean)? = null

    fun registerPeer(
        address: String,
        role: BlePeerRole = BlePeerRole.CLIENT
    ): BlePeerConnection {
        val peer = byAddress.getOrPut(address) { BlePeerConnection(address) }
        when (role) {
            BlePeerRole.CLIENT -> peer.hasClientSession = true
            BlePeerRole.SERVER -> peer.hasServerSession = true
        }
        logSnapshot()
        return peer
    }

    fun removePeer(address: String): BlePeerConnection? {
        val removed = byAddress.remove(address) ?: return null
        val nodeId = removed.nodeId
        Log.i(TAG, "BLE PEER DISCONNECTED: $address")
        if (nodeId != null) {
            Log.i(TAG, "BLE PEER REMOVED: $nodeId")
        }
        logSnapshot()
        return removed
    }

    fun removePeerByNodeId(nodeId: String): BlePeerConnection? {
        val peer = getPeerByNodeId(nodeId) ?: return null
        return removePeer(peer.address)
    }

    fun dropClientSession(address: String): BlePeerConnection? {
        val peer = byAddress[address] ?: return null
        peer.hasClientSession = false
        peer.gatt = null
        if (!peer.hasServerSession) {
            peer.rxCharacteristic = null
            peer.txCharacteristic = null
        }
        peer.clearClientWriteState()
        peer.connectionState = BleGattClientState.DISCONNECTED
        if (!peer.isLive()) {
            return removePeer(address)
        }
        logSnapshot()
        return peer
    }

    fun dropServerSession(address: String): BlePeerConnection? {
        val peer = byAddress[address] ?: return null
        peer.hasServerSession = false
        peer.serverSubscribed = false
        if (!peer.isLive()) {
            return removePeer(address)
        }
        logSnapshot()
        return peer
    }

    fun getPeer(address: String): BlePeerConnection? = byAddress[address]

    fun getPeerByNodeId(nodeId: String): BlePeerConnection? {
        if (nodeId.isBlank()) {
            return null
        }
        val matches = byAddress.values.filter { it.nodeId == nodeId }
        return matches.firstOrNull { it.canClientSend() }
            ?: matches.firstOrNull { it.canServerNotify() }
            ?: matches.firstOrNull { it.gatt != null }
            ?: matches.firstOrNull()
    }

    fun getConnectedPeers(): List<BlePeerConnection> {
        return byAddress.values.filter { it.isLive() }
    }

    fun getEligiblePeers(excludeNodeId: String? = null): List<BlePeerConnection> {
        return getConnectedPeers().filter { peer ->
            val id = peer.nodeId
            id != null && id.isNotBlank() && id != excludeNodeId
        }
    }

    fun meshPeers(): List<Peer> {
        return getEligiblePeers().mapNotNull { peer ->
            peer.nodeId?.let { Peer(it) }
        }
    }

    fun sendToPeer(nodeId: String, encoded: String): Boolean {
        val peer = getPeerByNodeId(nodeId)
        if (peer == null || !peer.isLive()) {
            Log.e(TAG, "BLE SEND TO PEER: $nodeId failed (not connected)")
            return false
        }
        logTargetResolution(peer, nodeId)
        Log.i(TAG, "BLE SEND TO PEER: $nodeId")
        val client = clientSend
        if (peer.canClientSend() && client != null) {
            return client.invoke(peer, encoded)
        }
        val server = serverSend
        if (peer.canServerNotify() && server != null) {
            return server.invoke(peer, encoded)
        }
        recordedSends.add(nodeId to encoded)
        return true
    }

    fun bindNodeId(address: String, nodeId: String): BlePeerConnection? {
        if (nodeId.isBlank()) {
            return getPeer(address)
        }
        val peer = getPeer(address) ?: registerPeer(address)
        peer.nodeId = nodeId
        Log.i(TAG, "BLE PEER REGISTERED: $nodeId")
        if (peer.gatt != null || peer.rxCharacteristic != null) {
            logPeerReady(peer)
        } else {
            logSnapshot()
        }
        return peer
    }

    /**
     * Updates the existing peer for [address] in place. Never creates a second object.
     */
    fun updateLiveLink(
        address: String,
        gatt: BluetoothGatt? = null,
        rx: BluetoothGattCharacteristic? = null,
        tx: BluetoothGattCharacteristic? = null,
        notificationsEnabled: Boolean? = null,
        mtu: Int? = null
    ): BlePeerConnection? {
        val peer = getPeer(address) ?: return null
        if (gatt != null) {
            peer.gatt = gatt
        }
        if (rx != null) {
            peer.rxCharacteristic = rx
        }
        if (tx != null) {
            peer.txCharacteristic = tx
        }
        if (notificationsEnabled != null) {
            peer.notificationsEnabled = notificationsEnabled
        }
        if (mtu != null) {
            peer.negotiatedMtu = mtu
        }
        logPeerReady(peer)
        return peer
    }

    fun logPeerReady(peer: BlePeerConnection) {
        Log.i(TAG, "BLE PEER READY:")
        Log.i(TAG, "nodeId=${peer.nodeId ?: "unbound"}")
        Log.i(TAG, "address=${peer.address}")
        Log.i(TAG, "gatt=${idOf(peer.gatt)}")
        Log.i(TAG, "rx=${charLabel(peer.rxCharacteristic)}")
        Log.i(TAG, "tx=${charLabel(peer.txCharacteristic)}")
        Log.i(TAG, "mtu=${peer.negotiatedMtu}")
        logPeerMap()
    }

    fun registerLogicalPeer(nodeId: String): BlePeerConnection {
        val address = "logical:$nodeId"
        val peer = registerPeer(address, BlePeerRole.CLIENT)
        peer.nodeId = nodeId
        peer.notificationsEnabled = true
        peer.handshakeState = BleHandshakeState.SUCCESS
        peer.connectionState = BleGattClientState.NOTIFICATIONS_ENABLED
        return peer
    }

    fun recordedSends(): List<Pair<String, String>> = recordedSends.toList()

    fun peerCount(): Int = getConnectedPeers().size

    fun nodeIds(): List<String> {
        return getEligiblePeers().mapNotNull { it.nodeId }
    }

    fun clear() {
        byAddress.clear()
        recordedSends.clear()
        logSnapshot()
    }

    fun logSnapshot() {
        val ids = nodeIds()
        Log.i(TAG, "BLE PEER COUNT: ${peerCount()}")
        Log.i(TAG, "BLE PEER LIST: $ids")
        Log.i(TAG, "BLE ACTIVE CONNECTIONS: ${peerCount()}")
        logPeerMap()
    }

    private fun logTargetResolution(peer: BlePeerConnection, nodeId: String) {
        Log.i(TAG, "MESH TARGET RESOLUTION")
        Log.i(TAG, "targetNodeId=$nodeId")
        Log.i(TAG, "targetMac=${peer.address}")
        Log.i(TAG, "targetGatt=${idOf(peer.gatt)}")
        Log.i(TAG, "targetRxCharacteristic=${rxLabel(peer)}")
        Log.i(TAG, "targetGattDeviceMac=${gattDeviceMac(peer)}")
        Log.i(TAG, "canClientSend=${peer.canClientSend()} canServerNotify=${peer.canServerNotify()}")
        logPeerMap()
    }

    private fun logPeerMap() {
        Log.i(TAG, "BLE PEER MAP:")
        if (byAddress.isEmpty()) {
            Log.i(TAG, "nodeId -> MAC -> GATT -> RX characteristic (empty)")
            return
        }
        byAddress.values.forEach { peer ->
            Log.i(
                TAG,
                "${peer.nodeId ?: "unbound"} -> ${peer.address} -> GATT=${idOf(peer.gatt)} -> RX=${rxLabel(peer)}"
            )
        }
    }

    private fun rxLabel(peer: BlePeerConnection): String = charLabel(peer.rxCharacteristic)

    private fun charLabel(characteristic: BluetoothGattCharacteristic?): String {
        val value = characteristic ?: return "null"
        return "${value.uuid}#${idOf(value)}"
    }

    private fun gattDeviceMac(peer: BlePeerConnection): String {
        return try {
            peer.gatt?.device?.address ?: "null"
        } catch (_: SecurityException) {
            "permission-denied"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
