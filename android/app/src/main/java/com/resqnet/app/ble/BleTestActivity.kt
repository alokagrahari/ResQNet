package com.resqnet.app.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.resqnet.app.emergency.SosCoordinator
import com.resqnet.app.ui.ResQUi
import com.resqnet.ble.BleGattClientState
import com.resqnet.ble.BleHandshakeState
import com.resqnet.ble.BlePermissions
import com.resqnet.ble.uiLabel
import com.resqnet.location.LocationFix
import com.resqnet.mesh.EmergencyPayload
import com.resqnet.mesh.EmergencySos
import com.resqnet.mesh.Packet

/**
 * Hosts the proven BLE test path (advertise, scan, GATT, HELLO/ACK, PING/PONG, DATA)
 * inside the main application. Does not change GATT UUIDs or handshake behavior.
 */
class BleTestActivity : AppCompatActivity() {
    private lateinit var ui: ResQUi
    private val coordinator by lazy { SosCoordinator.get(this) }
    private val bleLink by lazy { coordinator.bleLink }
    private val bleScanner get() = bleLink.scanner
    private val bleAdvertiser get() = bleLink.advertiser
    private val bleGattServer get() = bleLink.gattServer
    private val bleGattClient get() = bleLink.gattClient
    private val discoveredByAddress = linkedMapOf<String, BluetoothDevice>()

    private var status = "Idle"
    private var devices = listOf<String>()
    private var selectedAddress: String? = null
    private var isScanning = false
    private var isAdvertising = false
    private var connectionState = BleGattClientState.DISCONNECTED
    private var handshakeLabel = "Not started"
    private var handshakeSuccess = false
    private var serverPeerReady = false
    private var lastMessage = "None"

    private lateinit var statusView: TextView
    private lateinit var deviceView: TextView
    private lateinit var connectionView: TextView
    private lateinit var handshakeView: TextView
    private lateinit var lastMessageView: TextView
    private lateinit var peersView: TextView
    private lateinit var nodeIdView: TextView
    private lateinit var destField: EditText
    private lateinit var connectButton: View
    private lateinit var disconnectButton: View
    private lateinit var stopAdvertiseButton: View
    private lateinit var stopScanButton: View
    private lateinit var pingButton: View
    private lateinit var dataButton: View
    private lateinit var packetButton: View
    private lateinit var deviceList: LinearLayout
    private var sosInFlight = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* BLE classes report missing-permission errors if denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ResQUi(this)
        requestBluetoothPermissions()
        bindGattCallbacks()
        setContentView(buildScreen())
        refreshUi()
    }

    override fun onDestroy() {
        bleScanner.stopScanning()
        super.onDestroy()
    }

    private fun requestBluetoothPermissions() {
        val needed = (BlePermissions.requiredRuntimePermissions() + LOCATION_PERMISSIONS)
            .distinct()
            .filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun bindGattCallbacks() {
        bleGattServer.onClientConnected = { device ->
            status = "GATT CLIENT CONNECTED: ${safeAddress(device)}"
            refreshUi()
        }
        bleGattServer.onClientDisconnected = { device ->
            bleLink.handleServerDisconnected(device)
            handshakeLabel = "Not started"
            handshakeSuccess = false
            serverPeerReady = false
            status = "GATT CLIENT DISCONNECTED: ${safeAddress(device)}"
            refreshUi()
        }
        bleGattServer.onNotificationEnabled = { device ->
            status = "GATT NOTIFICATION ENABLED: ${safeAddress(device)}"
            serverPeerReady = true
            refreshUi()
        }
        bleGattServer.onHelloReceived = { remoteNodeId ->
            handshakeLabel = "HELLO received"
            status = "HELLO received: $remoteNodeId"
            refreshUi()
        }
        bleGattServer.onAckSent = { localNodeId ->
            handshakeLabel = "ACK sent"
            status = "ACK sent: ACK:$localNodeId"
            refreshUi()
        }
        bleGattServer.onPingReceived = { messageId ->
            status = "PING received: $messageId"
            refreshUi()
        }
        bleGattServer.onPongSent = { messageId ->
            status = "PONG sent: $messageId"
            refreshUi()
        }
        bleGattServer.onPongReceived = { messageId ->
            status = "PING/PONG SUCCESS"
            lastMessage = "PONG:$messageId"
            refreshUi()
        }
        bleGattServer.onDataReceived = { messageId, payload ->
            status = "DATA RECEIVED"
            lastMessage = "DATA:$messageId:$payload"
            refreshUi()
        }
        bleGattClient.onPingPongSuccess = { messageId ->
            status = "PING/PONG SUCCESS"
            lastMessage = "PONG:$messageId"
            refreshUi()
        }
        bleGattClient.onDataReceived = { messageId, payload ->
            status = "DATA RECEIVED"
            lastMessage = "DATA:$messageId:$payload"
            refreshUi()
        }
        coordinator.onMeshDelivered = { packet ->
            runOnUiThread { showDestinationEmergency(packet) }
        }
        coordinator.meshEngine.onMessageForwarded = { packet ->
            runOnUiThread { showRelayEmergency(packet) }
        }
    }

    private fun buildScreen(): View {
        val l = ui.screenColumn()
        l.addView(ui.header("BLE test", true) { finish() })
        l.addView(
            ui.caption("GATT: advertise, scan, connect, HELLO/ACK, PING/PONG, DATA. Mesh: Packet over the same RX/TX.")
                .apply { layoutParams = ui.lp(mb = 12) }
        )

        val info = ui.card()
        val col = ui.cardColumn(info)
        statusView = ui.body("Idle")
        deviceView = ui.body("None")
        connectionView = ui.body("Disconnected")
        handshakeView = ui.body("Not started")
        lastMessageView = ui.body("None")
        peersView = ui.body("Connected peers: 0")
        col.addView(ui.caption("Status"))
        col.addView(statusView.apply { layoutParams = ui.lp(mb = 8) })
        col.addView(ui.caption("Device found"))
        col.addView(deviceView.apply { layoutParams = ui.lp(mb = 8) })
        col.addView(ui.caption("Connection"))
        col.addView(connectionView.apply { layoutParams = ui.lp(mb = 8) })
        col.addView(ui.caption("Handshake"))
        col.addView(handshakeView.apply { layoutParams = ui.lp(mb = 8) })
        col.addView(ui.caption("Last message"))
        col.addView(lastMessageView.apply { layoutParams = ui.lp(mb = 8) })
        col.addView(ui.caption("Connected peers"))
        col.addView(peersView.apply { layoutParams = ui.lp(mb = 8) })
        nodeIdView = ui.body(bleLink.localNodeId)
        col.addView(ui.caption("This node"))
        col.addView(nodeIdView.apply { layoutParams = ui.lp(mb = 8) })
        destField = ui.textField(
            "Destination nodeId (C)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        col.addView(ui.caption("MESH destination"))
        col.addView(destField.apply { layoutParams = ui.lp(height = ui.dp(48), mb = 0) })
        l.addView(info)

        pingButton = ui.primaryButton("Send PING").apply {
            setOnClickListener { sendPing() }
        }
        dataButton = ui.tonalButton("Send Test Message").apply {
            setOnClickListener { sendTestMessage() }
        }
        packetButton = ui.primaryButton("🚨 SEND EMERGENCY SOS").apply {
            setOnClickListener { sendMeshPacket() }
        }
        connectButton = ui.primaryButton("CONNECT").apply {
            setOnClickListener { connectSelected() }
        }
        disconnectButton = ui.secondaryButton("DISCONNECT").apply {
            setOnClickListener {
                val address = selectedAddress ?: devices.lastOrNull()
                if (address != null) {
                    bleGattClient.disconnect(address)
                } else {
                    bleGattClient.disconnect()
                }
                status = "Disconnecting..."
                refreshUi()
            }
        }
        l.addView(pingButton)
        l.addView(dataButton)
        l.addView(packetButton)
        l.addView(connectButton)
        l.addView(disconnectButton)

        l.addView(
            ui.primaryButton("START ADVERTISING").apply {
                setOnClickListener { startAdvertising() }
            }
        )
        stopAdvertiseButton = ui.secondaryButton("STOP ADVERTISING").apply {
            setOnClickListener {
                bleAdvertiser.stopAdvertising()
                bleGattServer.stop()
                bleLink.handleServerStopped()
                isAdvertising = false
                serverPeerReady = false
                status = "Advertising stopped"
                refreshUi()
            }
        }
        l.addView(stopAdvertiseButton)

        l.addView(
            ui.primaryButton("START SCAN").apply {
                setOnClickListener { startScan() }
            }
        )
        stopScanButton = ui.secondaryButton("STOP SCAN").apply {
            setOnClickListener {
                bleScanner.stopScanning()
                isScanning = false
                status = "Scan stopped"
                refreshUi()
            }
        }
        l.addView(stopScanButton)

        l.addView(ui.sectionTitle("Discovered devices"))
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l.addView(deviceList)

        return ui.scroll(l)
    }

    private fun startAdvertising() {
        status = "GATT server starting..."
        refreshUi()
        val serverStarted = bleGattServer.start(
            onStarted = {
                val advertised = bleAdvertiser.startAdvertising(
                    successCallback = {
                        isAdvertising = true
                        status = "Advertising started"
                        refreshUi()
                    },
                    errorCallback = { message ->
                        isAdvertising = false
                        bleGattServer.stop()
                        status = message
                        refreshUi()
                    }
                )
                if (advertised) {
                    isAdvertising = true
                    status = "Starting advertising..."
                    refreshUi()
                }
            },
            onError = { message ->
                isAdvertising = false
                status = message
                refreshUi()
            }
        )
        if (!serverStarted) {
            isAdvertising = false
            refreshUi()
        }
    }

    private fun startScan() {
        devices = emptyList()
        discoveredByAddress.clear()
        selectedAddress = null
        val started = bleScanner.startScanning(
            deviceCallback = { device ->
                val address = safeAddress(device)
                discoveredByAddress[address] = device
                if (!devices.contains(address)) {
                    devices = devices + address
                }
                selectedAddress = address
                status = "BLE DEVICE FOUND: $address"
                refreshUi()
            },
            errorCallback = { message ->
                isScanning = false
                status = message
                refreshUi()
            }
        )
        if (started) {
            isScanning = true
            status = "Scanning..."
            refreshUi()
        }
    }

    private fun connectSelected() {
        val address = selectedAddress ?: devices.lastOrNull()
        val device = address?.let { discoveredByAddress[it] }
        if (address != null) {
            selectedAddress = address
        }
        if (device == null) {
            status = "GATT CLIENT ERROR: no device selected"
            refreshUi()
            return
        }
        bleScanner.stopScanning()
        isScanning = false
        connectionState = BleGattClientState.CONNECTING
        handshakeLabel = "Not started"
        handshakeSuccess = false
        status = "Connecting..."
        refreshUi()
        val started = bleGattClient.connect(
            device = device,
            onConnected = {
                connectionState = BleGattClientState.CONNECTED
                status = "Connected"
                refreshUi()
            },
            onDisconnected = {
                connectionState = BleGattClientState.DISCONNECTED
                handshakeLabel = "Not started"
                handshakeSuccess = false
                status = "Disconnected"
                refreshUi()
            },
            onError = { message ->
                connectionState = BleGattClientState.DISCONNECTED
                handshakeLabel = "Failed"
                status = message
                refreshUi()
            },
            onServicesDiscovered = {
                connectionState = BleGattClientState.SERVICES_DISCOVERED
                status = "Services Discovered"
                refreshUi()
            },
            onReady = {
                connectionState = BleGattClientState.NOTIFICATIONS_ENABLED
                handshakeLabel = "Waiting"
                status = "Notifications enabled"
                refreshUi()
            },
            onHandshake = { handshakeState ->
                handshakeLabel = handshakeState.uiLabel()
                if (handshakeState == BleHandshakeState.SUCCESS) {
                    handshakeSuccess = true
                    status = "HELLO/ACK SUCCESS"
                } else if (handshakeState == BleHandshakeState.FAILED) {
                    handshakeSuccess = false
                    status = "Handshake Failed"
                }
                refreshUi()
            }
        )
        if (!started) {
            connectionState = BleGattClientState.DISCONNECTED
            refreshUi()
        }
    }

    private fun sendPing() {
        val sent = if (handshakeSuccess) {
            bleGattClient.sendPing()
        } else {
            bleGattServer.sendPing()
        }
        status = if (sent) "PING sent" else "PING NOT SENT"
        refreshUi()
    }

    private fun sendTestMessage() {
        val sent = if (handshakeSuccess) {
            bleGattClient.sendTestData()
        } else {
            bleGattServer.sendTestData()
        }
        status = if (sent) "Test message sent" else "DATA NOT SENT"
        refreshUi()
    }

    private fun sendMeshPacket() {
        val dest = destField.text.toString().trim()
        if (dest.isEmpty()) {
            status = "PACKET NOT SENT: enter C nodeId as destination"
            refreshUi()
            return
        }
        if (sosInFlight) {
            return
        }
        if (!coordinator.hasLocationPermission()) {
            showGpsUnavailable()
            requestBluetoothPermissions()
            return
        }
        sosInFlight = true
        status = "Obtaining GPS..."
        lastMessage = "Obtaining GPS..."
        refreshUi()
        coordinator.refreshLocation { fix ->
            runOnUiThread {
                sosInFlight = false
                if (fix == null) {
                    showGpsUnavailable()
                    return@runOnUiThread
                }
                sendEmergencyWithFix(dest, fix)
            }
        }
    }

    private fun sendEmergencyWithFix(dest: String, fix: LocationFix) {
        val payload = EmergencyPayload.encode(fix.latitude, fix.longitude)
        val latLine = gpsLatLine(fix)
        val lonLine = gpsLonLine(fix)
        logDemo("🚨 EMERGENCY SOS CREATED")
        logDemo(latLine)
        logDemo(lonLine)
        val packet = coordinator.meshEngine.sendMessage(dest, payload)
        logDemo("MESH SEND START")
        val peers = bleLink.transport.getPeers()
        val sendNote = if (peers.isEmpty()) {
            "PACKET NOT SENT: no BLE peer (handshake first)"
        } else {
            "PACKET SENT: ${packet.messageId} dest=$dest"
        }
        status = "🚨 EMERGENCY SOS CREATED"
        lastMessage = "$latLine\n$lonLine\nMESH SEND START\n$sendNote"
        refreshUi()
    }

    private fun showGpsUnavailable() {
        logDemo("GPS LOCATION UNAVAILABLE")
        status = "GPS LOCATION UNAVAILABLE"
        lastMessage = "GPS LOCATION UNAVAILABLE"
        refreshUi()
    }

    private fun showRelayEmergency(packet: Packet) {
        val sos = EmergencyPayload.parse(packet.payload) ?: return
        val gps = formatGpsPair(sos)
        val peer = relayPeerLabel(packet)
        logDemo("🚨 EMERGENCY RECEIVED")
        logDemo("SOURCE: ${packet.sourceNodeId}")
        logDemo("GPS: $gps")
        logDemo("MESH FORWARD")
        logDemo("BLE SEND TO PEER: $peer")
        status = "🚨 EMERGENCY RECEIVED"
        lastMessage =
            "SOURCE: ${packet.sourceNodeId}\nGPS: $gps\nMESH FORWARD\nBLE SEND TO PEER: $peer"
        refreshUi()
    }

    private fun showDestinationEmergency(packet: Packet) {
        val sos = EmergencyPayload.parse(packet.payload)
        if (sos == null) {
            status = "PACKET RECEIVED"
            lastMessage = "MESH:${packet.messageId}:${packet.payload}"
            refreshUi()
            return
        }
        val lat = sos.latitude?.let { EmergencyPayload.formatCoord(it) } ?: "unavailable"
        val lon = sos.longitude?.let { EmergencyPayload.formatCoord(it) } ?: "unavailable"
        val type = sos.type.ifBlank { "unknown" }
        val priority = sos.priority.ifBlank { "unknown" }
        val message = sos.message.ifBlank { "unavailable" }
        logDemo("🚨 EMERGENCY RECEIVED")
        logDemo("TYPE: $type")
        logDemo("PRIORITY: $priority")
        logDemo("GPS LAT: $lat")
        logDemo("GPS LON: $lon")
        logDemo("MESSAGE: $message")
        status = "🚨 EMERGENCY RECEIVED"
        lastMessage =
            "TYPE: $type\nPRIORITY: $priority\nGPS LAT: $lat\nGPS LON: $lon\nMESSAGE: $message"
        refreshUi()
    }

    private fun relayPeerLabel(packet: Packet): String {
        val local = bleLink.localNodeId
        val others = bleLink.connections.nodeIds().filter {
            it != local && it != packet.sourceNodeId
        }
        val dest = packet.destinationNodeId
        return when {
            dest in others -> dest
            others.size == 1 -> others.first()
            others.isNotEmpty() -> others.joinToString(", ")
            else -> dest
        }
    }

    private fun formatGpsPair(sos: EmergencySos?): String {
        if (sos == null || !sos.hasValidCoordinates) {
            return "unavailable"
        }
        return "${EmergencyPayload.formatCoord(sos.latitude!!)}, ${EmergencyPayload.formatCoord(sos.longitude!!)}"
    }

    private fun gpsLatLine(fix: LocationFix): String {
        val suffix = if (fix.isLastKnown) " (last-known)" else ""
        return "GPS LAT$suffix: ${EmergencyPayload.formatCoord(fix.latitude)}"
    }

    private fun gpsLonLine(fix: LocationFix): String {
        val suffix = if (fix.isLastKnown) " (last-known)" else ""
        return "GPS LON$suffix: ${EmergencyPayload.formatCoord(fix.longitude)}"
    }

    private fun logDemo(line: String) {
        Log.i(DEMO_LOG_TAG, line)
    }

    private fun refreshUi() {
        if (!::statusView.isInitialized) return
        statusView.text = status
        deviceView.text = selectedAddress ?: devices.lastOrNull() ?: "None"
        connectionView.text = when (connectionState) {
            BleGattClientState.DISCONNECTED -> "Disconnected"
            BleGattClientState.CONNECTING -> "Connecting"
            BleGattClientState.CONNECTED -> "Connected"
            BleGattClientState.SERVICES_DISCOVERED -> "Services Discovered"
            BleGattClientState.NOTIFICATIONS_ENABLED -> "Notifications enabled"
        }
        handshakeView.text = handshakeLabel
        lastMessageView.text = lastMessage
        val connected = bleLink.connections.getConnectedPeers()
        val nodeIds = bleLink.connections.nodeIds()
        val peerLines = if (nodeIds.isEmpty()) {
            "Connected peers: ${connected.size}"
        } else {
            "Connected peers: ${connected.size}\n" +
                nodeIds.joinToString("\n") { "- $it" }
        }
        peersView.text = peerLines

        val selectedClientActive =
            selectedAddress?.let { bleLink.connections.getPeer(it)?.isClientActive() } == true
        val anyReady = connected.any { it.canClientSend() || it.canServerNotify() }
        val activeAddress = selectedAddress ?: devices.lastOrNull()
        pingButton.isEnabled = anyReady
        dataButton.isEnabled = anyReady
        packetButton.isEnabled = nodeIds.isNotEmpty()
        connectButton.isEnabled = activeAddress != null && !selectedClientActive
        disconnectButton.isEnabled = selectedClientActive || connected.isNotEmpty()
        stopAdvertiseButton.isEnabled = isAdvertising
        stopScanButton.isEnabled = isScanning

        deviceList.removeAllViews()
        devices.forEach { address ->
            val btn = ui.secondaryButton(address)
            btn.layoutParams = ui.lp(height = ui.dp(48), mb = 8)
            btn.setOnClickListener {
                selectedAddress = address
                status = "Device Found:\n$address"
                refreshUi()
            }
            deviceList.addView(btn)
        }
        if (devices.isEmpty()) {
            deviceList.addView(
                ui.caption("None yet. Start scan on this phone and advertise on the other.")
            )
        }
    }

    @Suppress("MissingPermission")
    private fun safeAddress(device: BluetoothDevice): String {
        return try {
            device.address
        } catch (_: SecurityException) {
            "unknown"
        }
    }

    companion object {
        private const val DEMO_LOG_TAG = "ResQNetBLE"
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
