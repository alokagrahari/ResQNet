package com.resqnet.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

class BleGattServer(context: Context) {

    companion object {
        private const val TAG = "ResQNetBLE"

        fun mapGattStatus(status: Int): String = when (status) {
            BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS"
            BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE"
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ->
                "GATT_INSUFFICIENT_AUTHENTICATION"
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH ->
                "GATT_INVALID_ATTRIBUTE_LENGTH"
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED"
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED"
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED"
            else -> "GATT status $status"
        }

        fun precheckServerStart(
            bluetoothUnavailable: Boolean,
            bluetoothDisabled: Boolean,
            missingConnectPermission: Boolean,
            alreadyRunning: Boolean
        ): String? = when {
            alreadyRunning -> "GATT server already running"
            bluetoothUnavailable -> "Bluetooth is unavailable on this device"
            bluetoothDisabled -> "Bluetooth is disabled"
            missingConnectPermission -> "BLUETOOTH_CONNECT permission is missing"
            else -> null
        }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    private var gattServer: BluetoothGattServer? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var isRunning = false
    private val mtuByAddress = ConcurrentHashMap<String, Int>()

    private val subscribedDevices = ConcurrentHashMap.newKeySet<BluetoothDevice>()
    private val helloGuard = HelloDuplicateGuard()
    private val localNodeId = BleNodeId(appContext).getOrCreate()

    private var startedCallback: (() -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    var onClientConnected: ((BluetoothDevice) -> Unit)? = null
    var onClientDisconnected: ((BluetoothDevice) -> Unit)? = null
    /**
     * Mesh peer registry hook. Independent of [onClientConnected] so the test
     * UI can replace that callback without dropping multi-peer registration.
     */
    var onPeerConnected: ((BluetoothDevice) -> Unit)? = null
    var onRxWrite: ((BluetoothDevice, ByteArray) -> Unit)? = null
    var onNotificationEnabled: ((BluetoothDevice) -> Unit)? = null
    /**
     * Mesh peer registry hook. Independent of [onNotificationEnabled] so the
     * test UI can replace that callback without dropping re-registration.
     */
    var onPeerSubscribed: ((BluetoothDevice) -> Unit)? = null
    /**
     * Mesh peer registry hook. Independent of [onHelloReceived] so the test UI
     * can replace that callback without dropping nodeId registration.
     */
    var onRemoteNodeIdentified: ((BluetoothDevice, String) -> Unit)? = null
    /**
     * Mesh peer registry hook. Independent of [onClientDisconnected] so the
     * test UI cannot skip unregistering a connected peer.
     */
    var onPeerDisconnected: ((BluetoothDevice) -> Unit)? = null
    /**
     * Dedicated MESH ingest hook. Independent of [onRxWrite] / UI callbacks
     * so `MESH:v1` is never treated as an ignored test message.
     */
    var onMeshPayload: ((BluetoothDevice, String) -> Unit)? = null
    var onHelloReceived: ((String) -> Unit)? = null
    var onAckSent: ((String) -> Unit)? = null
    var onPingReceived: ((String) -> Unit)? = null
    var onPongSent: ((String) -> Unit)? = null
    var onPongReceived: ((String) -> Unit)? = null
    var onDataReceived: ((String, String) -> Unit)? = null
    var peerNodeIdOf: ((BluetoothDevice) -> String?)? = null
    var onPeerMtuChanged: ((BluetoothDevice, Int) -> Unit)? = null

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS &&
                service.uuid == BleConstants.SERVICE_UUID
            ) {
                isRunning = true
                Log.d(TAG, "GATT SERVICE ADDED")
                val callback = startedCallback
                startedCallback = null
                if (callback != null) {
                    mainHandler.post { callback.invoke() }
                }
            } else {
                isRunning = false
                val message =
                    "GATT SERVER ERROR: failed to add service (${mapGattStatus(status)})"
                Log.e(TAG, message)
                notifyError(message)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(device, status, newState)
            val address = safeAddress(device)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(
                    TAG,
                    "GATT SERVER ERROR: connection state change failed " +
                        "for $address (${mapGattStatus(status)})"
                )
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "GATT CLIENT CONNECTED: $address")
                        Log.i(TAG, "BLE PEER CONNECTED: $address")
                        onPeerConnected?.invoke(device)
                        val callback = onClientConnected
                        mainHandler.post { callback?.invoke(device) }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    subscribedDevices.remove(device)
                    helloGuard.onDisconnected(address)
                    Log.d(TAG, "GATT CLIENT DISCONNECTED: $address")
                    onPeerDisconnected?.invoke(device)
                    val callback = onClientDisconnected
                    mainHandler.post { callback?.invoke(device) }
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            val payload = value ?: ByteArray(0)
            if (characteristic.uuid != BleConstants.RX_CHARACTERISTIC_UUID) {
                sendResponseIfNeeded(
                    device,
                    requestId,
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    offset,
                    payload,
                    responseNeeded
                )
                return
            }
            if (preparedWrite) {
                sendResponseIfNeeded(
                    device,
                    requestId,
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    offset,
                    payload,
                    responseNeeded
                )
                return
            }
            val address = safeAddress(device)
            val nodeId = peerNodeIdOf?.invoke(device) ?: "unbound"
            val decoded = BleHandshakeProtocol.decodeUtf8(payload)
            Log.i(TAG, "GATT RX WRITE")
            Log.i(TAG, "RX DEVICE ADDRESS=$address")
            Log.i(TAG, "RX DEVICE NODE ID=$nodeId")
            Log.i(TAG, "RX BYTE COUNT=${payload.size}")
            Log.i(TAG, "RX DECODED=$decoded")
            sendResponseIfNeeded(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                payload,
                responseNeeded
            )
            handleIncomingRx(device, payload)
            val callback = onRxWrite
            mainHandler.post { callback?.invoke(device, payload) }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onDescriptorWriteRequest(
                device,
                requestId,
                descriptor,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            val payload = value ?: ByteArray(0)
            if (descriptor.uuid != BleConstants.CCCD_UUID) {
                sendResponseIfNeeded(
                    device,
                    requestId,
                    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                    offset,
                    payload,
                    responseNeeded
                )
                return
            }
            val enabled = payload.contentEquals(
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            )
            if (enabled) {
                subscribedDevices.add(device)
                Log.d(TAG, "GATT NOTIFICATION ENABLED: ${safeAddress(device)}")
                onPeerSubscribed?.invoke(device)
                val callback = onNotificationEnabled
                mainHandler.post { callback?.invoke(device) }
            } else {
                subscribedDevices.remove(device)
                Log.d(TAG, "GATT NOTIFICATION DISABLED: ${safeAddress(device)}")
                onPeerDisconnected?.invoke(device)
            }
            @Suppress("DEPRECATION")
            descriptor.value = payload
            sendResponseIfNeeded(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                payload,
                responseNeeded
            )
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            val value = if (subscribedDevices.contains(device)) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            sendResponseIfNeeded(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                value,
                responseNeeded = true
            )
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            super.onNotificationSent(device, status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(
                    TAG,
                    "GATT SERVER ERROR: notification send failed for " +
                        "${safeAddress(device)} (${mapGattStatus(status)})"
                )
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            super.onMtuChanged(device, mtu)
            val address = safeAddress(device)
            mtuByAddress[address] = mtu
            Log.i(
                TAG,
                "GATT SERVER MTU CHANGED: mtu=$mtu address=$address"
            )
            onPeerMtuChanged?.invoke(device, mtu)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(
        onStarted: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (isRunning && gattServer != null) {
            mainHandler.post { onError("GATT server already running") }
            return true
        }

        startedCallback = onStarted
        errorCallback = onError

        val adapter = bluetoothAdapter
        val error = precheckServerStart(
            bluetoothUnavailable = adapter == null,
            bluetoothDisabled = adapter?.isEnabled != true,
            missingConnectPermission = !hasConnectPermission(),
            alreadyRunning = false
        )
        if (error != null) {
            val message = "GATT SERVER ERROR: $error"
            Log.e(TAG, message)
            notifyError(message)
            return false
        }

        Log.d(TAG, "GATT SERVER STARTING")
        val manager = bluetoothManager
        if (manager == null) {
            val message = "GATT SERVER ERROR: Bluetooth manager is unavailable"
            Log.e(TAG, message)
            notifyError(message)
            return false
        }

        return try {
            val server = manager.openGattServer(appContext, serverCallback)
            if (server == null) {
                val message = "GATT SERVER ERROR: failed to open GATT server"
                Log.e(TAG, message)
                notifyError(message)
                return false
            }
            gattServer = server
            val service = buildResqnetService()
            val added = server.addService(service)
            if (!added) {
                val message = "GATT SERVER ERROR: addService returned false"
                Log.e(TAG, message)
                stop()
                notifyError(message)
                false
            } else {
                true
            }
        } catch (e: SecurityException) {
            val message = "GATT SERVER ERROR: BLUETOOTH_CONNECT permission is missing"
            Log.e(TAG, message, e)
            notifyError(message)
            false
        } catch (e: Exception) {
            val message = "GATT SERVER ERROR: ${e.message ?: "unknown error"}"
            Log.e(TAG, message, e)
            notifyError(message)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val remaining = subscribedDevices.toList()
        subscribedDevices.clear()
        remaining.forEach { device ->
            onPeerDisconnected?.invoke(device)
        }
        helloGuard.clear()
        rxCharacteristic = null
        txCharacteristic = null
        mtuByAddress.clear()
        startedCallback = null
        try {
            gattServer?.clearServices()
            gattServer?.close()
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        gattServer = null
        isRunning = false
        errorCallback = null
        Log.d(TAG, "GATT SERVER STOPPED")
    }

    @SuppressLint("MissingPermission")
    fun sendNotification(data: ByteArray): Boolean {
        val server = gattServer
        val characteristic = txCharacteristic
        if (server == null || characteristic == null) {
            Log.e(TAG, "GATT SERVER ERROR: cannot notify, server is not running")
            return false
        }
        if (subscribedDevices.isEmpty()) {
            Log.e(TAG, "GATT SERVER ERROR: no subscribed clients for notification")
            return false
        }
        var allAccepted = true
        subscribedDevices.forEach { device ->
            val accepted = notifyDevice(server, device, characteristic, data)
            if (!accepted) {
                allAccepted = false
            }
        }
        return allAccepted
    }

    fun findSubscribedByAddress(address: String): BluetoothDevice? {
        return subscribedDevices.firstOrNull { safeAddress(it) == address }
    }

    @SuppressLint("MissingPermission")
    fun sendNotificationTo(device: BluetoothDevice, data: ByteArray): Boolean {
        val server = gattServer
        val characteristic = txCharacteristic
        if (server == null || characteristic == null) {
            Log.e(TAG, "GATT SERVER ERROR: cannot notify, server is not running")
            return false
        }
        Log.d(TAG, "MESH SEND START")
        Log.d(TAG, "MESH PAYLOAD=${String(data, Charsets.UTF_8)}")
        val accepted = notifyDevice(server, device, characteristic, data)
        Log.d(TAG, "notifyCharacteristicChanged() return value=$accepted")
        return accepted
    }

    private fun handleIncomingRx(device: BluetoothDevice, payload: ByteArray) {
        val message = BleHandshakeProtocol.decodeUtf8(payload)
        val test = BleTestMessageProtocol.parse(message)
        val parsedType = BleInboundClassifier.typeLabel(message)
        Log.d(TAG, "GATT RX RECEIVED:")
        Log.d(TAG, "rawBytes=${payload.contentToString()}")
        Log.d(TAG, "decoded=$message")
        Log.d(TAG, "PARSED MESSAGE TYPE=$parsedType")
        when (BleInboundClassifier.classify(message)) {
            BleInboundClassifier.Kind.HELLO -> {
                val handshake = BleHandshakeProtocol.parse(message)
                if (handshake is ParsedHandshake.Hello) {
                    val identified = onRemoteNodeIdentified
                    mainHandler.post { identified?.invoke(device, handshake.nodeId) }
                    val connectionId = safeAddress(device)
                    if (!helloGuard.shouldAck(connectionId)) {
                        Log.d(TAG, "HELLO duplicate ignored: $connectionId")
                        return
                    }
                    Log.d(TAG, "HELLO RECEIVED: ${handshake.nodeId}")
                    val helloCallback = onHelloReceived
                    mainHandler.post { helloCallback?.invoke(handshake.nodeId) }
                    sendAck(device)
                }
            }
            BleInboundClassifier.Kind.MESH -> {
                Log.i(TAG, "BLE MESSAGE TYPE=MESH")
                val meshCallback = onMeshPayload
                if (meshCallback != null) {
                    meshCallback.invoke(device, message)
                } else {
                    Log.e(TAG, "BLE MESH RX DROPPED: no mesh handler")
                }
            }
            else -> handleIncomingTestMessage(device, test)
        }
    }

    private fun handleIncomingTestMessage(
        device: BluetoothDevice,
        parsed: ParsedTestMessage
    ) {
        when (parsed) {
            is ParsedTestMessage.Ping -> {
                Log.d(TAG, "PING RECEIVED: ${parsed.messageId}")
                val callback = onPingReceived
                mainHandler.post { callback?.invoke(parsed.messageId) }
                if (BleTestMessageProtocol.shouldGenerateResponse(parsed)) {
                    sendPong(device, parsed.messageId)
                }
            }
            is ParsedTestMessage.Pong -> {
                Log.d(TAG, "PONG RECEIVED: ${parsed.messageId}")
                Log.d(TAG, "PING/PONG SUCCESS")
                val callback = onPongReceived
                mainHandler.post { callback?.invoke(parsed.messageId) }
            }
            is ParsedTestMessage.Data -> {
                Log.d(TAG, "DATA RECEIVED")
                Log.d(TAG, "DATA MESSAGE ID=${parsed.messageId}")
                Log.d(TAG, "DATA PAYLOAD=${parsed.payload}")
                val callback = onDataReceived
                mainHandler.post { callback?.invoke(parsed.messageId, parsed.payload) }
            }
            ParsedTestMessage.Invalid -> {
                // PING/PONG/DATA only. MESH is dispatched via onMeshPayload.
            }
        }
    }

    fun sendPing(): Boolean {
        val messageId = BleTestMessageProtocol.newMessageId()
        val text = BleTestMessageProtocol.ping(messageId)
        Log.d(TAG, "PING SEND START")
        Log.d(TAG, "PING PAYLOAD=$text")
        return notifyAllSubscribed(text.toByteArray(Charsets.UTF_8), "PING")
    }

    fun sendTestData(): Boolean {
        val messageId = BleTestMessageProtocol.newMessageId()
        val payload = BleTestMessageProtocol.TEST_PAYLOAD
        val text = BleTestMessageProtocol.data(messageId, payload)
        Log.d(TAG, "DATA SEND START")
        Log.d(TAG, "DATA MESSAGE ID=$messageId")
        Log.d(TAG, "DATA PAYLOAD=$payload")
        return notifyAllSubscribed(text.toByteArray(Charsets.UTF_8), "DATA")
    }

    fun hasSubscribedClient(): Boolean = subscribedDevices.isNotEmpty()

    fun localRx(): BluetoothGattCharacteristic? = rxCharacteristic

    fun localTx(): BluetoothGattCharacteristic? = txCharacteristic

    fun mtuFor(address: String): Int = mtuByAddress[address] ?: 23

    @SuppressLint("MissingPermission")
    private fun sendPong(device: BluetoothDevice, messageId: String) {
        val text = BleTestMessageProtocol.pong(messageId)
        Log.d(TAG, "PONG SEND START")
        Log.d(TAG, "PONG PAYLOAD=$text")
        val server = gattServer
        val characteristic = txCharacteristic
        if (server == null || characteristic == null) {
            Log.e(TAG, "GATT SERVER ERROR: cannot send PONG, server is not running")
            return
        }
        val accepted = notifyDevice(
            server,
            device,
            characteristic,
            text.toByteArray(Charsets.UTF_8)
        )
        Log.d(TAG, "notifyCharacteristicChanged() return value=$accepted")
        if (accepted) {
            Log.d(TAG, "PONG SENT: $text")
            val callback = onPongSent
            mainHandler.post { callback?.invoke(messageId) }
        } else {
            Log.e(TAG, "GATT SERVER ERROR: PONG notifyCharacteristicChanged failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyAllSubscribed(data: ByteArray, kind: String): Boolean {
        val server = gattServer
        val characteristic = txCharacteristic
        if (server == null || characteristic == null) {
            Log.e(TAG, "GATT SERVER ERROR: cannot send $kind, server is not running")
            return false
        }
        if (subscribedDevices.isEmpty()) {
            Log.e(TAG, "GATT SERVER ERROR: no subscribed clients for $kind")
            return false
        }
        var allAccepted = true
        subscribedDevices.forEach { device ->
            val accepted = notifyDevice(server, device, characteristic, data)
            Log.d(TAG, "notifyCharacteristicChanged() return value=$accepted")
            if (!accepted) {
                allAccepted = false
            }
        }
        if (allAccepted && kind == "DATA") {
            Log.d(TAG, "DATA SENT")
        }
        if (allAccepted && kind == "PING") {
            Log.d(TAG, "PING SENT")
        }
        return allAccepted
    }

    @SuppressLint("MissingPermission")
    private fun sendAck(device: BluetoothDevice) {
        val server = gattServer
        val characteristic = txCharacteristic
        val connectionId = safeAddress(device)
        if (server == null || characteristic == null) {
            helloGuard.onDisconnected(connectionId)
            Log.e(TAG, "GATT SERVER ERROR: cannot send ACK, server is not running")
            return
        }
        if (!subscribedDevices.contains(device)) {
            helloGuard.onDisconnected(connectionId)
            Log.e(TAG, "GATT SERVER ERROR: cannot send ACK, client is not subscribed")
            return
        }
        val text = BleHandshakeProtocol.ack(localNodeId)
        Log.d(TAG, "ACK SEND START")
        Log.d(TAG, "ACK PAYLOAD: $text")
        val accepted = notifyDevice(
            server,
            device,
            characteristic,
            text.toByteArray(Charsets.UTF_8)
        )
        Log.d(TAG, "notifyCharacteristicChanged() return value = $accepted")
        if (accepted) {
            Log.d(TAG, "ACK SENT: $text")
            val callback = onAckSent
            mainHandler.post { callback?.invoke(localNodeId) }
        } else {
            helloGuard.onDisconnected(connectionId)
            Log.e(TAG, "GATT SERVER ERROR: ACK notifyCharacteristicChanged failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyDevice(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = server.notifyCharacteristicChanged(
                    device,
                    characteristic,
                    false,
                    data
                )
                result == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT SERVER ERROR: notify permission missing", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "GATT SERVER ERROR: ${e.message ?: "notify failed"}", e)
            false
        }
    }

    private fun buildResqnetService(): BluetoothGattService {
        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val rx = BluetoothGattCharacteristic(
            BleConstants.RX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val tx = BluetoothGattCharacteristic(
            BleConstants.TX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(
            BleConstants.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or
                BluetoothGattDescriptor.PERMISSION_WRITE
        )
        @Suppress("DEPRECATION")
        cccd.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        tx.addDescriptor(cccd)
        rxCharacteristic = rx
        txCharacteristic = tx

        service.addCharacteristic(rx)
        service.addCharacteristic(tx)
        return service
    }

    @SuppressLint("MissingPermission")
    private fun sendResponseIfNeeded(
        device: BluetoothDevice,
        requestId: Int,
        status: Int,
        offset: Int,
        value: ByteArray,
        responseNeeded: Boolean
    ) {
        if (!responseNeeded) {
            return
        }
        try {
            gattServer?.sendResponse(device, requestId, status, offset, value)
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT SERVER ERROR: sendResponse permission missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "GATT SERVER ERROR: ${e.message ?: "sendResponse failed"}", e)
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeAddress(device: BluetoothDevice): String {
        return try {
            device.address
        } catch (_: SecurityException) {
            "unknown"
        }
    }

    private fun notifyError(message: String) {
        val callback = errorCallback
        startedCallback = null
        errorCallback = null
        if (callback != null) {
            mainHandler.post { callback.invoke(message) }
        }
    }
}
