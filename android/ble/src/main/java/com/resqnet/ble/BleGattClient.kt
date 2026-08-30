package com.resqnet.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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

class BleGattClient(
    context: Context,
    private val connections: BleConnectionManager = BleConnectionManager()
) {

    companion object {
        private const val TAG = "ResQNetBLE"
        /** Samsung Android 9 often fails discoverServices() if called immediately. */
        private const val SERVICE_DISCOVERY_DELAY_MS = 300L
        /** Default ATT MTU is 23 (20-byte writes). Emergency MESH exceeds 185. */
        private const val REQUESTED_MTU = 517

        fun mapConnectionState(androidState: Int): BleGattClientState = when (androidState) {
            BluetoothProfile.STATE_CONNECTED -> BleGattClientState.CONNECTED
            BluetoothProfile.STATE_CONNECTING -> BleGattClientState.CONNECTING
            BluetoothProfile.STATE_DISCONNECTED -> BleGattClientState.DISCONNECTED
            BluetoothProfile.STATE_DISCONNECTING -> BleGattClientState.DISCONNECTED
            else -> BleGattClientState.DISCONNECTED
        }

        fun isSuccessfulConnect(status: Int, newState: Int): Boolean {
            return status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothProfile.STATE_CONNECTED
        }

        fun precheckConnect(
            bluetoothUnavailable: Boolean,
            bluetoothDisabled: Boolean,
            missingConnectPermission: Boolean,
            alreadyConnected: Boolean,
            alreadyConnecting: Boolean
        ): String? = when {
            alreadyConnected -> "GATT client already connected to this device"
            alreadyConnecting -> "GATT client already connecting to this device"
            bluetoothUnavailable -> "Bluetooth is unavailable on this device"
            bluetoothDisabled -> "Bluetooth is disabled"
            missingConnectPermission -> "BLUETOOTH_CONNECT permission is missing"
            else -> null
        }

        fun validateDiscoveredAttributes(
            serviceFound: Boolean,
            rxFound: Boolean,
            txFound: Boolean,
            cccdFound: Boolean
        ): String? = when {
            !serviceFound -> "ResQNet service missing"
            !rxFound -> "RX characteristic missing"
            !txFound -> "TX characteristic missing"
            !cccdFound -> "notification setup failure: CCCD missing"
            else -> null
        }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val localNodeId = BleNodeId(appContext).getOrCreate()
    private val sessionUi = ConcurrentHashMap<String, SessionUi>()

    var onPingPongSuccess: ((String) -> Unit)? = null
    var onDataReceived: ((String, String) -> Unit)? = null
    var onAckReceived: ((String, String) -> Unit)? = null
    var onMeshPayload: ((String, String) -> Unit)? = null
    var onUnhandledMessage: ((String, String) -> Unit)? = null
    var onLinkDisconnected: ((String) -> Unit)? = null

    private class SessionUi(
        val onConnected: (BluetoothDevice) -> Unit,
        val onDisconnected: (BluetoothDevice) -> Unit,
        val onError: (String) -> Unit,
        val onServicesDiscovered: () -> Unit,
        val onReady: () -> Unit,
        val onHandshake: (BleHandshakeState) -> Unit
    )

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)
            val device = gatt.device
            val address = safeAddress(device)
            val session = sessionOf(address)
            Log.d(
                TAG,
                "onConnectionStateChange: ${diag(address)} " +
                    "status=$status (${BleGattServer.mapGattStatus(status)}) " +
                    "newState=$newState retainedGatt=${session?.gatt != null} " +
                    "sameGatt=${session?.gatt === gatt}"
            )

            if (isSuccessfulConnect(status, newState)) {
                val peer = connections.registerPeer(address, BlePeerRole.CLIENT)
                peer.device = device
                peer.hasClientSession = true
                peer.connectionState = BleGattClientState.CONNECTED
                connections.updateLiveLink(address, gatt = gatt)
                Log.i(TAG, "BLE PEER CONNECTED: $address")
                Log.d(TAG, "GATT CLIENT CONNECTED: $address ${diag(address)}")
                val callback = sessionUi[address]?.onConnected
                mainHandler.post { callback?.invoke(device) }
                startServiceDiscovery(peer, gatt)
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                session?.connectionState = BleGattClientState.DISCONNECTED
                session?.clearClientWriteState()
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val message =
                        "GATT CLIENT ERROR: disconnect status ${BleGattServer.mapGattStatus(status)}"
                    Log.e(TAG, message)
                    notifyError(address, message)
                }
                Log.d(TAG, "GATT CLIENT DISCONNECTED: $address ${diag(address)}")
                closeGatt(session, gatt)
                val callback = sessionUi[address]?.onDisconnected
                val linkCallback = onLinkDisconnected
                mainHandler.post {
                    callback?.invoke(device)
                    linkCallback?.invoke(address)
                }
                return
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                session?.connectionState = BleGattClientState.DISCONNECTED
                val message =
                    "GATT CLIENT ERROR: connection failed for $address " +
                        "(${BleGattServer.mapGattStatus(status)})"
                Log.e(TAG, message)
                closeGatt(session, gatt)
                notifyError(address, message)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val address = safeAddress(gatt.device)
            val session = sessionOf(address)
            Log.d(
                TAG,
                "onServicesDiscovered callback: ${diag(address)} " +
                    "status=$status (${BleGattServer.mapGattStatus(status)}) " +
                    "sameGatt=${session?.gatt === gatt}"
            )
            Log.d(
                TAG,
                "status value: $status (${BleGattServer.mapGattStatus(status)})"
            )
            if (session != null) {
                session.gatt = gatt
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val message =
                    "GATT CLIENT ERROR: service discovery failure " +
                        "(${BleGattServer.mapGattStatus(status)})"
                Log.e(TAG, message)
                notifyError(address, message)
                return
            }
            Log.d(TAG, "GATT SERVICES DISCOVERED: $address")
            logAllDiscoveredServices(gatt)
            if (session != null) {
                handleServicesDiscovered(session, gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            val address = safeAddress(gatt.device)
            val session = sessionOf(address)
            Log.d(
                TAG,
                "onDescriptorWrite callback: ${diag(address)} " +
                    "descriptor=${descriptor.uuid} " +
                    "status=$status (${BleGattServer.mapGattStatus(status)})"
            )
            Log.d(
                TAG,
                "descriptor status: $status (${BleGattServer.mapGattStatus(status)})"
            )
            if (descriptor.uuid != BleConstants.CCCD_UUID) {
                Log.d(TAG, "onDescriptorWrite ignored (not CCCD): ${descriptor.uuid}")
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val message =
                    "GATT CLIENT ERROR: notification setup failure " +
                        "(${BleGattServer.mapGattStatus(status)})"
                Log.e(TAG, message)
                notifyError(address, message)
                return
            }
            if (session == null) {
                return
            }
            session.connectionState = BleGattClientState.NOTIFICATIONS_ENABLED
            connections.updateLiveLink(
                address,
                gatt = gatt,
                rx = session.rxCharacteristic,
                tx = session.txCharacteristic,
                notificationsEnabled = true,
                mtu = session.negotiatedMtu
            )
            Log.d(TAG, "GATT NOTIFICATIONS ENABLED: $address ${diag(address)}")
            Log.i(TAG, "BLE PEER NOTIFICATIONS ENABLED: ${session.nodeId ?: address}")
            Log.d(TAG, "PHASE D TRIGGER CHECK")
            Log.d(TAG, "address=$address")
            val notificationsEnabled = session.notificationsEnabled
            val rxPresent = session.rxCharacteristic != null
            val connectionStillUp = BleHandshakeMachine.isConnectionStillUp(session.connectionState)
            Log.d(TAG, "notificationsEnabled=$notificationsEnabled")
            Log.d(TAG, "rxCharacteristic=${if (rxPresent) "non-null" else "null"}")
            Log.d(TAG, "connectionState=${session.connectionState}")
            Log.d(TAG, "helloAttempted=${session.helloAttempted}")
            val skipReason = BleHandshakeMachine.helloSkipReason(
                gattNonNull = true,
                rxCharacteristicPresent = rxPresent,
                notificationsEnabled = notificationsEnabled,
                connectionStillUp = connectionStillUp,
                helloAttempted = session.helloAttempted
            )
            if (skipReason != null) {
                Log.e(TAG, "HELLO NOT SENT: $skipReason")
            } else {
                session.helloAttempted = true
                Log.d(TAG, "HELLO TRIGGER ENTERED")
                try {
                    sendHello(session, gatt)
                } catch (t: Throwable) {
                    Log.e(TAG, "HELLO NOT SENT: ${t.message ?: t.javaClass.simpleName}", t)
                }
            }
            val callback = sessionUi[address]?.onReady
            if (callback != null) {
                mainHandler.post { callback.invoke() }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (characteristic.uuid != BleConstants.RX_CHARACTERISTIC_UUID) {
                return
            }
            val session = sessionOf(safeAddress(gatt.device)) ?: return
            val helloText = session.pendingHelloText
            val pingText = session.pendingPingText
            val dataText = session.pendingDataText
            val meshText = session.pendingMeshText
            session.pendingHelloText = null
            session.pendingPingText = null
            session.pendingDataText = null
            session.pendingMeshText = null
            if (helloText != null) {
                Log.d(TAG, "HELLO WRITE CALLBACK")
                Log.d(TAG, "status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val message =
                        "GATT CLIENT ERROR: HELLO write failure " +
                            "(${BleGattServer.mapGattStatus(status)})"
                    Log.e(TAG, message)
                    updateHandshake(session, BleHandshakeMachine.afterHelloWriteCallback(false))
                    return
                }
                Log.d(TAG, "HELLO SENT: $helloText")
                updateHandshake(session, BleHandshakeMachine.afterHelloWriteCallback(true))
                return
            }
            if (pingText != null) {
                Log.d(TAG, "PING WRITE CALLBACK")
                Log.d(TAG, "status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(
                        TAG,
                        "GATT CLIENT ERROR: PING write failure " +
                            "(${BleGattServer.mapGattStatus(status)})"
                    )
                    return
                }
                Log.d(TAG, "PING SENT: $pingText")
                return
            }
            if (dataText != null) {
                Log.d(TAG, "DATA WRITE CALLBACK")
                Log.d(TAG, "status=$status")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(
                        TAG,
                        "GATT CLIENT ERROR: DATA write failure " +
                            "(${BleGattServer.mapGattStatus(status)})"
                    )
                    return
                }
                Log.d(TAG, "DATA SENT")
                return
            }
            if (meshText != null) {
                Log.i(TAG, "MESH WRITE CALLBACK")
                Log.i(TAG, "MESH WRITE CALLBACK STATUS=$status")
                Log.i(
                    TAG,
                    "MESH WRITE CALLBACK GATT=${BleConnectionManager.idOf(gatt)} " +
                        "device=${safeAddress(gatt.device)}"
                )
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(
                        TAG,
                        "GATT CLIENT ERROR: MESH write failure " +
                            "(${BleGattServer.mapGattStatus(status)})"
                    )
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleTxNotification(gatt, characteristic, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return
            }
            val value = characteristic.value ?: ByteArray(0)
            handleTxNotification(gatt, characteristic, value)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            val address = safeAddress(gatt.device)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                connections.updateLiveLink(address, gatt = gatt, mtu = mtu)
            }
            Log.i(
                TAG,
                "GATT MTU CHANGED: mtu=$mtu status=$status " +
                    "address=$address gatt=${BleConnectionManager.idOf(gatt)}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        onConnected: (BluetoothDevice) -> Unit,
        onDisconnected: (BluetoothDevice) -> Unit,
        onError: (String) -> Unit,
        onServicesDiscovered: () -> Unit = {},
        onReady: () -> Unit = {},
        onHandshake: (BleHandshakeState) -> Unit = {}
    ): Boolean {
        val address = safeAddress(device)
        val existing = connections.getPeer(address)
        val error = precheckConnect(
            bluetoothUnavailable = bluetoothAdapter == null,
            bluetoothDisabled = bluetoothAdapter?.isEnabled != true,
            missingConnectPermission = !hasConnectPermission(),
            alreadyConnected = existing?.isClientActive() == true,
            alreadyConnecting = existing?.connectionState == BleGattClientState.CONNECTING &&
                existing.hasClientSession
        )
        if (error != null) {
            val message = "GATT CLIENT ERROR: $error"
            Log.e(TAG, message)
            mainHandler.post { onError(message) }
            return false
        }

        sessionUi[address] = SessionUi(
            onConnected = onConnected,
            onDisconnected = onDisconnected,
            onError = onError,
            onServicesDiscovered = onServicesDiscovered,
            onReady = onReady,
            onHandshake = onHandshake
        )
        val session = connections.registerPeer(address, BlePeerRole.CLIENT)
        session.device = device
        session.handshakeState = BleHandshakeState.NOT_STARTED
        session.pendingHelloText = null
        session.pendingPingText = null
        session.pendingDataText = null
        session.pendingMeshText = null
        session.helloAttempted = false
        session.notificationsEnabled = false
        session.connectionState = BleGattClientState.CONNECTING
        Log.i(TAG, "BLE PEER CONNECTING: $address")
        Log.d(TAG, "GATT CLIENT CONNECTING: $address")

        return try {
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M,
                    mainHandler
                )
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            }
            if (gatt == null) {
                session.connectionState = BleGattClientState.DISCONNECTED
                val message = "GATT CLIENT ERROR: connectGatt returned null"
                Log.e(TAG, message)
                notifyError(address, message)
                false
            } else {
                if (session.gatt == null) {
                    session.gatt = gatt
                }
                Log.d(
                    TAG,
                    "connectGatt returned: ${diag(address)} " +
                        "retainedSameAsReturned=${session.gatt === gatt}"
                )
                true
            }
        } catch (e: SecurityException) {
            session.connectionState = BleGattClientState.DISCONNECTED
            val message = "GATT CLIENT ERROR: BLUETOOTH_CONNECT permission is missing"
            Log.e(TAG, message, e)
            notifyError(address, message)
            false
        } catch (e: Exception) {
            session.connectionState = BleGattClientState.DISCONNECTED
            val message = "GATT CLIENT ERROR: ${e.message ?: "connect failed"}"
            Log.e(TAG, message, e)
            notifyError(address, message)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val target = connections.getConnectedPeers().firstOrNull { it.hasClientSession }
        if (target != null) {
            disconnect(target.address)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        val session = connections.getPeer(address)
        val gatt = session?.gatt
        if (gatt == null) {
            if (session != null) {
                session.connectionState = BleGattClientState.DISCONNECTED
            }
            return
        }
        try {
            gatt.disconnect()
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT CLIENT ERROR: disconnect permission missing", e)
            closeGatt(session, gatt)
        } catch (e: Exception) {
            Log.e(TAG, "GATT CLIENT ERROR: ${e.message ?: "disconnect failed"}", e)
            closeGatt(session, gatt)
        }
    }

    fun currentState(): BleGattClientState {
        val clients = connections.getConnectedPeers().filter { it.hasClientSession }
        if (clients.isEmpty()) {
            return BleGattClientState.DISCONNECTED
        }
        return clients.maxBy { it.connectionState.ordinal }.connectionState
    }

    fun currentState(address: String): BleGattClientState {
        return connections.getPeer(address)?.connectionState
            ?: BleGattClientState.DISCONNECTED
    }

    fun handshakeState(address: String): BleHandshakeState {
        return connections.getPeer(address)?.handshakeState
            ?: BleHandshakeState.NOT_STARTED
    }

    @SuppressLint("MissingPermission")
    private fun startServiceDiscovery(session: BlePeerConnection, gatt: BluetoothGatt) {
        session.cancelPendingDiscovery(mainHandler)
        session.gatt = gatt
        val address = session.address
        Log.d(
            TAG,
            "BEFORE DISCOVER SERVICES: ${diag(address)} " +
                "delayMs=$SERVICE_DISCOVERY_DELAY_MS state=${session.connectionState} gattRetained=true"
        )
        val runnable = Runnable {
            session.pendingDiscovery = null
            val retained = session.gatt
            Log.d(
                TAG,
                "DISCOVER SERVICES DELAY FIRED: ${diag(address)} " +
                    "state=${session.connectionState} retainedGatt=${retained != null} " +
                    "sameGatt=${retained === gatt}"
            )
            if (session.connectionState == BleGattClientState.DISCONNECTED) {
                val message =
                    "GATT CLIENT ERROR: skipping discoverServices because state is DISCONNECTED"
                Log.e(TAG, message)
                notifyError(address, message)
                return@Runnable
            }
            val target = retained ?: gatt
            session.gatt = target
            Log.d(TAG, "GATT SERVICE DISCOVERY STARTED: $address ${diag(address)}")
            val started = try {
                target.discoverServices()
            } catch (e: SecurityException) {
                Log.e(TAG, "GATT CLIENT ERROR: BLUETOOTH_CONNECT permission is missing", e)
                notifyError(address, "GATT CLIENT ERROR: BLUETOOTH_CONNECT permission is missing")
                false
            } catch (e: Exception) {
                val message = "GATT CLIENT ERROR: ${e.message ?: "discoverServices failed"}"
                Log.e(TAG, message, e)
                notifyError(address, message)
                false
            }
            Log.d(
                TAG,
                "discoverServices() return value: $started ${diag(address)}"
            )
            Log.d(
                TAG,
                "AFTER DISCOVER SERVICES REQUEST: ${diag(address)} started=$started"
            )
            if (!started) {
                val message = "GATT CLIENT ERROR: discoverServices() returned false"
                Log.e(TAG, message)
                notifyError(address, message)
            }
        }
        session.pendingDiscovery = runnable
        mainHandler.postDelayed(runnable, SERVICE_DISCOVERY_DELAY_MS)
        Log.d(
            TAG,
            "DISCOVER SERVICES SCHEDULED: delayMs=$SERVICE_DISCOVERY_DELAY_MS ${diag(address)}"
        )
    }

    @SuppressLint("MissingPermission")
    private fun logAllDiscoveredServices(gatt: BluetoothGatt) {
        val services = gatt.services
        Log.d(TAG, "DISCOVERED SERVICE COUNT: ${services.size}")
        if (services.isEmpty()) {
            Log.e(TAG, "GATT CLIENT ERROR: onServicesDiscovered GATT_SUCCESS but service list is empty")
        }
        for (service in services) {
            Log.d(TAG, "DISCOVERED SERVICE UUID: ${service.uuid}")
            for (characteristic in service.characteristics) {
                Log.d(TAG, "  CHAR UUID: ${characteristic.uuid}")
                for (descriptor in characteristic.descriptors) {
                    Log.d(TAG, "    DESC UUID: ${descriptor.uuid}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleServicesDiscovered(session: BlePeerConnection, gatt: BluetoothGatt) {
        val service = gatt.getService(BleConstants.SERVICE_UUID)
        val rx = service?.getCharacteristic(BleConstants.RX_CHARACTERISTIC_UUID)
        val tx = service?.getCharacteristic(BleConstants.TX_CHARACTERISTIC_UUID)
        val cccd = tx?.getDescriptor(BleConstants.CCCD_UUID)
        Log.d(
            TAG,
            "ResQNet service lookup result: found=${service != null} uuid=${BleConstants.SERVICE_UUID}"
        )
        Log.d(
            TAG,
            "RX lookup result: found=${rx != null} uuid=${BleConstants.RX_CHARACTERISTIC_UUID}"
        )
        Log.d(
            TAG,
            "TX lookup result: found=${tx != null} uuid=${BleConstants.TX_CHARACTERISTIC_UUID}"
        )
        Log.d(
            TAG,
            "CCCD lookup result: found=${cccd != null} uuid=${BleConstants.CCCD_UUID}"
        )
        val error = validateDiscoveredAttributes(
            serviceFound = service != null,
            rxFound = rx != null,
            txFound = tx != null,
            cccdFound = cccd != null
        )
        if (error != null) {
            val message = "GATT CLIENT ERROR: $error"
            Log.e(TAG, message)
            notifyError(session.address, message)
            return
        }
        Log.d(TAG, "RESQNET SERVICE FOUND")
        Log.d(TAG, "RX CHARACTERISTIC FOUND")
        Log.d(TAG, "TX CHARACTERISTIC FOUND")
        session.connectionState = BleGattClientState.SERVICES_DISCOVERED
        connections.updateLiveLink(
            session.address,
            gatt = gatt,
            rx = rx,
            tx = tx,
            mtu = session.negotiatedMtu
        )
        val discoveredCallback = sessionUi[session.address]?.onServicesDiscovered
        mainHandler.post { discoveredCallback?.invoke() }
        enableTxNotifications(gatt, tx!!, cccd!!)
    }

    @SuppressLint("MissingPermission")
    private fun enableTxNotifications(
        gatt: BluetoothGatt,
        tx: BluetoothGattCharacteristic,
        cccd: BluetoothGattDescriptor
    ) {
        val address = safeAddress(gatt.device)
        val notificationSet = try {
            gatt.setCharacteristicNotification(tx, true)
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT CLIENT ERROR: BLUETOOTH_CONNECT permission is missing", e)
            notifyError(address, "GATT CLIENT ERROR: BLUETOOTH_CONNECT permission is missing")
            return
        } catch (e: Exception) {
            val message = "GATT CLIENT ERROR: ${e.message ?: "setCharacteristicNotification failed"}"
            Log.e(TAG, message, e)
            notifyError(address, message)
            return
        }
        Log.d(
            TAG,
            "setCharacteristicNotification() return value: $notificationSet ${diag(address)}"
        )
        if (!notificationSet) {
            val message = "GATT CLIENT ERROR: setCharacteristicNotification() returned false"
            Log.e(TAG, message)
            notifyError(address, message)
            return
        }
        val writeStarted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeDescriptor(
                    cccd,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
                Log.d(TAG, "writeDescriptor() return value: $result ${diag(address)}")
                result == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                val result = gatt.writeDescriptor(cccd)
                Log.d(TAG, "writeDescriptor() return value: $result ${diag(address)}")
                result
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "GATT CLIENT ERROR: BLUETOOTH_CONNECT permission is missing", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "GATT CLIENT ERROR: ${e.message ?: "writeDescriptor failed"}", e)
            false
        }
        if (!writeStarted) {
            val message = "GATT CLIENT ERROR: writeDescriptor() returned false"
            Log.e(TAG, message)
            notifyError(address, message)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(session: BlePeerConnection?, gatt: BluetoothGatt?) {
        val address = session?.address ?: try {
            gatt?.device?.let { safeAddress(it) } ?: "none"
        } catch (_: Exception) {
            "unknown"
        }
        Log.d(TAG, "GATT CLIENT CLOSE: ${diag(address)} sameGatt=${session?.gatt === gatt}")
        session?.cancelPendingDiscovery(mainHandler)
        try {
            gatt?.close()
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        if (session != null && (session.gatt === gatt || gatt == null)) {
            connections.dropClientSession(session.address)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHello(session: BlePeerConnection, gatt: BluetoothGatt) {
        val rx = session.rxCharacteristic
        if (rx == null) {
            Log.e(TAG, "HELLO NOT SENT: RX characteristic is null")
            updateHandshake(session, BleHandshakeState.FAILED)
            return
        }
        session.handshakeState = BleHandshakeMachine.afterNotificationsEnabled()
        val text = BleHandshakeProtocol.hello(localNodeId)
        val bytes = text.toByteArray(Charsets.UTF_8)
        session.pendingHelloText = text
        rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        Log.d(TAG, "HELLO NODE ID=$localNodeId")
        Log.d(TAG, "HELLO PAYLOAD=$text")
        Log.d(TAG, "HELLO WRITE START")
        Log.d(TAG, "writeType=WRITE_TYPE_DEFAULT")
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    rx,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rx.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rx)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "HELLO NOT SENT: BLUETOOTH_CONNECT permission is missing", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "HELLO NOT SENT: ${e.message ?: "HELLO write failed"}", e)
            false
        }
        Log.d(TAG, "writeCharacteristic() return value=$started")
        updateHandshake(session, BleHandshakeMachine.afterHelloWriteAccepted(started))
        if (!started) {
            session.pendingHelloText = null
            Log.e(TAG, "HELLO WRITE REQUEST FAILED")
        }
    }

    fun canSendTestMessages(): Boolean {
        return connections.getConnectedPeers().any { it.canClientSend() }
    }

    fun sendPing(): Boolean {
        val session = firstReadyClient()
        if (session == null) {
            Log.e(TAG, "PING NOT SENT: handshake not complete")
            return false
        }
        val messageId = BleTestMessageProtocol.newMessageId()
        val text = BleTestMessageProtocol.ping(messageId)
        Log.d(TAG, "PING SEND START")
        Log.d(TAG, "PING PAYLOAD=$text")
        return writeRx(session, text, kind = "PING")
    }

    fun sendTestData(): Boolean {
        val session = firstReadyClient()
        if (session == null) {
            Log.e(TAG, "DATA NOT SENT: handshake not complete")
            return false
        }
        val messageId = BleTestMessageProtocol.newMessageId()
        val payload = BleTestMessageProtocol.TEST_PAYLOAD
        val text = BleTestMessageProtocol.data(messageId, payload)
        Log.d(TAG, "DATA SEND START")
        Log.d(TAG, "DATA MESSAGE ID=$messageId")
        Log.d(TAG, "DATA PAYLOAD=$payload")
        return writeRx(session, text, kind = "DATA")
    }

    fun sendPayload(text: String): Boolean {
        val session = firstReadyClient()
        if (session == null) {
            Log.e(TAG, "MESH NOT SENT: handshake not complete")
            return false
        }
        return sendPayload(session, text)
    }

    fun sendPayload(session: BlePeerConnection, text: String): Boolean {
        if (!session.canClientSend()) {
            Log.e(TAG, "MESH NOT SENT: handshake not complete")
            return false
        }
        Log.i(TAG, "MESH TARGET RESOLUTION")
        Log.i(TAG, "targetNodeId=${session.nodeId}")
        Log.i(TAG, "targetMac=${session.address}")
        Log.i(TAG, "targetGatt=${BleConnectionManager.idOf(session.gatt)}")
        Log.i(
            TAG,
            "targetRxCharacteristic=${session.rxCharacteristic?.uuid}#" +
                BleConnectionManager.idOf(session.rxCharacteristic)
        )
        Log.d(TAG, "MESH SEND START")
        Log.d(TAG, "MESH PAYLOAD=$text")
        return writeRx(session, text, kind = "MESH")
    }

    @SuppressLint("MissingPermission")
    private fun sendPong(session: BlePeerConnection, messageId: String) {
        val text = BleTestMessageProtocol.pong(messageId)
        Log.d(TAG, "PONG SEND START")
        Log.d(TAG, "PONG PAYLOAD=$text")
        writeRx(session, text, kind = "PONG")
    }

    @SuppressLint("MissingPermission")
    private fun writeRx(session: BlePeerConnection, text: String, kind: String): Boolean {
        val gatt = session.gatt
        val rx = session.rxCharacteristic
        if (gatt == null || rx == null) {
            Log.e(TAG, "$kind NOT SENT: RX characteristic is null")
            return false
        }
        when (kind) {
            "PING" -> session.pendingPingText = text
            "DATA" -> session.pendingDataText = text
            "MESH" -> session.pendingMeshText = text
        }
        val bytes = text.toByteArray(Charsets.UTF_8)
        val maxPayload = (session.negotiatedMtu - 3).coerceAtLeast(20)
        if (kind == "MESH") {
            Log.i(TAG, "MESH WRITE START")
            Log.i(
                TAG,
                "MESH WRITE GATT=${BleConnectionManager.idOf(gatt)} " +
                    "rx=${BleConnectionManager.idOf(rx)} " +
                    "bytes=${bytes.size} mtu=${session.negotiatedMtu} max=$maxPayload"
            )
            if (bytes.size > maxPayload) {
                Log.e(
                    TAG,
                    "MESH WRITE TOO LARGE: bytes=${bytes.size} max=$maxPayload " +
                        "mtu=${session.negotiatedMtu}"
                )
            }
        }
        rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    rx,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rx.value = bytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rx)
            }
        } catch (e: Exception) {
            Log.e(TAG, "$kind NOT SENT: ${e.message ?: "write failed"}", e)
            false
        }
        if (kind == "MESH") {
            Log.i(TAG, "MESH WRITE RESULT=$started")
        } else {
            Log.d(TAG, "writeCharacteristic() return value=$started")
        }
        if (!started) {
            if (kind == "PING") session.pendingPingText = null
            if (kind == "DATA") session.pendingDataText = null
            if (kind == "MESH") session.pendingMeshText = null
            Log.e(TAG, "$kind WRITE REQUEST FAILED")
        }
        return started
    }

    @SuppressLint("MissingPermission")
    private fun handleTxNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val address = safeAddress(gatt.device)
        val session = sessionOf(address)
        val message = BleHandshakeProtocol.decodeUtf8(value)
        Log.d(TAG, "GATT NOTIFICATION RECEIVED")
        Log.d(TAG, "characteristic UUID=${characteristic.uuid}")
        Log.d(TAG, "raw bytes=${value.contentToString()}")
        Log.d(TAG, "decoded string=$message")
        if (characteristic.uuid != BleConstants.TX_CHARACTERISTIC_UUID) {
            return
        }
        when (BleInboundClassifier.classify(message)) {
            BleInboundClassifier.Kind.ACK -> {
                val handshake = BleHandshakeProtocol.parse(message)
                if (handshake is ParsedHandshake.Ack) {
                    Log.d(TAG, "ACK RECEIVED: ${handshake.nodeId}")
                    if (session != null) {
                        updateHandshake(
                            session,
                            BleHandshakeMachine.afterAckReceived(session.handshakeState)
                        )
                    }
                    Log.d(TAG, "HELLO/ACK SUCCESS")
                    if (session != null) {
                        updateHandshake(session, BleHandshakeMachine.afterHandshakeSuccess())
                    }
                    val ackCallback = onAckReceived
                    if (ackCallback != null) {
                        ackCallback.invoke(address, handshake.nodeId)
                    }
                    try {
                        gatt.requestMtu(REQUESTED_MTU)
                    } catch (_: Exception) {
                    }
                }
            }
            BleInboundClassifier.Kind.MESH -> {
                Log.i(TAG, "BLE MESSAGE TYPE=MESH")
                val fromPeer = session?.nodeId ?: address
                Log.i(TAG, "BLE MESH RX FROM PEER=$fromPeer")
                val meshCallback = onMeshPayload
                if (meshCallback != null) {
                    meshCallback.invoke(address, message)
                } else {
                    Log.e(TAG, "BLE MESH RX DROPPED: no mesh handler for $address")
                }
            }
            else -> handleTestNotification(session, message)
        }
    }

    private fun handleTestNotification(session: BlePeerConnection?, message: String) {
        when (val parsed = BleTestMessageProtocol.parse(message)) {
            is ParsedTestMessage.Pong -> {
                Log.d(TAG, "PONG RECEIVED: ${parsed.messageId}")
                Log.d(TAG, "PING/PONG SUCCESS")
                val callback = onPingPongSuccess
                mainHandler.post { callback?.invoke(parsed.messageId) }
            }
            is ParsedTestMessage.Data -> {
                Log.d(TAG, "DATA RECEIVED")
                Log.d(TAG, "DATA MESSAGE ID=${parsed.messageId}")
                Log.d(TAG, "DATA PAYLOAD=${parsed.payload}")
                val callback = onDataReceived
                mainHandler.post { callback?.invoke(parsed.messageId, parsed.payload) }
            }
            is ParsedTestMessage.Ping -> {
                Log.d(TAG, "PING RECEIVED: ${parsed.messageId}")
                if (session != null && BleTestMessageProtocol.shouldGenerateResponse(parsed)) {
                    sendPong(session, parsed.messageId)
                }
            }
            ParsedTestMessage.Invalid -> {
                val parsedType = BleTestMessageProtocol.parsedType(
                    message,
                    ParsedTestMessage.Invalid
                )
                Log.d(TAG, "GATT notification ignored type=$parsedType")
                val callback = onUnhandledMessage
                val address = session?.address ?: ""
                if (BleInboundClassifier.classify(message) != BleInboundClassifier.Kind.MESH) {
                    mainHandler.post { callback?.invoke(address, message) }
                }
            }
        }
    }

    private fun updateHandshake(session: BlePeerConnection, newState: BleHandshakeState) {
        session.handshakeState = newState
        val callback = sessionUi[session.address]?.onHandshake
        mainHandler.post { callback?.invoke(newState) }
    }

    private fun firstReadyClient(): BlePeerConnection? {
        return connections.getConnectedPeers().firstOrNull { it.canClientSend() }
    }

    private fun sessionOf(address: String): BlePeerConnection? = connections.getPeer(address)

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

    private fun diag(address: String): String {
        return "address=$address thread=${Thread.currentThread().name}"
    }

    private fun notifyError(address: String, message: String) {
        val callback = sessionUi[address]?.onError
        if (callback != null) {
            mainHandler.post { callback.invoke(message) }
        }
    }
}
