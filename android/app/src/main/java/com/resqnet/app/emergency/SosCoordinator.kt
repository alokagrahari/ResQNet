package com.resqnet.app.emergency

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.resqnet.ble.BleModule
import com.resqnet.location.LocationFix
import com.resqnet.location.LocationHelper
import com.resqnet.location.LocationModule
import com.resqnet.location.SOSData
import com.resqnet.mesh.MeshEngine
import com.resqnet.mesh.MeshLogger
import com.resqnet.mesh.NodeIdentity
import com.resqnet.mesh.Packet
import com.resqnet.storage.StorageModule
import com.resqnet.storage.repository.EmergencyRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Wires Location → SOS data → Mesh ([com.resqnet.ble.BleTransport]).
 * MockTransport remains in `:mesh` for unit tests. BLE GATT HELLO/ACK and
 * PING/PONG/DATA are unchanged; mesh packets use the `MESH:` codec on RX/TX.
 */
class SosCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val locationHelper: LocationHelper = LocationModule.createHelper(appContext)
    val bleLink = BleModule.link(appContext)
    val nodeIdentity = NodeIdentity(bleLink.localNodeId)
    val meshEngine: MeshEngine = MeshEngine(nodeIdentity.nodeId, bleLink.transport)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val emergencyRepository = StorageModule.repository(appContext)
    private val syncManager = StorageModule.syncManager(appContext)

    @Volatile
    var lastFix: LocationFix? = null
        private set

    @Volatile
    var lastSosPacket: Packet? = null
        private set

    @Volatile
    var lastReceivedPacket: Packet? = null
        private set

    init {
        MeshLogger.logOutput = { tag, message ->
            val headline = when (tag) {
                "RECEIVE" -> "MESH RECEIVE"
                "VALIDATE" -> "MESH VALIDATION"
                "DEDUP" -> "MESH DEDUP"
                "TTL" -> "MESH TTL"
                "FORWARD" -> "MESH FORWARD"
                "DELIVERED" -> "MESH DELIVER"
                "DEST" -> "MESH DESTINATION CHECK"
                else -> null
            }
            if (headline != null) {
                Log.i(MESH_LOG_TAG, headline)
            }
            Log.i(MESH_LOG_TAG, "[$tag] $message")
        }
        bleLink.transport.onPacketReceived = { packet ->
            meshEngine.receivePacket(packet)
        }
        meshEngine.onMessageReceived = { packet ->
            lastReceivedPacket = packet
            onMeshDelivered?.invoke(packet)
        }
    }

    var onMeshDelivered: ((Packet) -> Unit)? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun refreshLocation(onDone: (LocationFix?) -> Unit) {
        locationHelper.getCurrentFix(
            onFixReceived = { fix ->
                lastFix = fix
                onDone(fix)
            },
            onLocationError = { onDone(null) }
        )
    }

    fun sendSos(emergencyType: String, onResult: (Packet?, SOSData?) -> Unit) {
        refreshLocation { fix ->
            if (fix == null) {
                onResult(null, null)
                return@refreshLocation
            }
            val sos = SOSData.from(emergencyType, fix)
            val payload = JSONObject()
                .put("type", sos.type)
                .put("latitude", sos.latitude)
                .put("longitude", sos.longitude)
                .put("accuracy", sos.accuracy.toDouble())
                .put("timestamp", sos.timestamp)
                .toString()
            val packet = meshEngine.sendMessage("*", payload)
            lastSosPacket = packet
            ioScope.launch {
                emergencyRepository.saveEmergency(
                    EmergencyRecord(
                        messageId = packet.messageId,
                        sourceNodeId = packet.sourceNodeId,
                        destinationNodeId = packet.destinationNodeId,
                        payload = packet.payload,
                        latitude = sos.latitude,
                        longitude = sos.longitude,
                        accuracy = sos.accuracy,
                        ttl = packet.ttl,
                        hopCount = packet.hopCount,
                        timestamp = sos.timestamp,
                        emergencyType = sos.type
                    )
                )
                syncManager.syncPending()
            }
            onResult(packet, sos)
        }
    }

    companion object {
        private const val MESH_LOG_TAG = "ResQNetBLE"
        @Volatile
        private var instance: SosCoordinator? = null

        fun get(context: Context): SosCoordinator {
            val existing = instance
            if (existing != null) {
                return existing
            }
            return synchronized(this) {
                instance ?: SosCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }
}
