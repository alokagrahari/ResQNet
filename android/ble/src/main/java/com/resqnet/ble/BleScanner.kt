package com.resqnet.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BleScanner(context: Context) {

    companion object {
        private const val TAG = "ResQNetBLE"

        fun mapScanError(errorCode: Int): String = when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                "Scan already started"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                "BLE scan application registration failed"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
                "BLE scan internal error"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                "BLE scan feature unsupported on this phone"
            else ->
                "BLE scan failed with error code $errorCode"
        }

        fun precheckScanStart(
            bluetoothUnavailable: Boolean,
            bluetoothDisabled: Boolean,
            missingScanPermission: Boolean,
            scannerUnavailable: Boolean,
            alreadyRunning: Boolean
        ): String? = when {
            alreadyRunning -> "Scan already running"
            bluetoothUnavailable -> "Bluetooth is unavailable on this device"
            bluetoothDisabled -> "Bluetooth is disabled"
            missingScanPermission -> "BLUETOOTH_SCAN permission is missing"
            scannerUnavailable -> "BLE scanner is unavailable"
            else -> null
        }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var isScanning = false
    private var deviceCallback: ((BluetoothDevice) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    var devicesFound: Int = 0
        private set

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.i(TAG, "onScanResult callbackType=$callbackType")
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            Log.i(TAG, "BLE BATCH RESULTS: count=${results.size}")
            results.forEach(::handleScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            isScanning = false
            val message = mapScanError(errorCode)
            Log.e(TAG, message)
            notifyError(message, clearCallbacks = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val address = try {
            result.device.address
        } catch (_: SecurityException) {
            "unknown"
        }
        val deviceName = try {
            result.scanRecord?.deviceName ?: result.device.name
        } catch (_: SecurityException) {
            null
        }
        val rssi = result.rssi
        val record = result.scanRecord
        val serviceUuids = record?.serviceUuids?.map { it.uuid } ?: emptyList()
        val rawBytes = toHex(record?.bytes)

        Log.i(TAG, "BLE RESULT RECEIVED: address=$address, rssi=$rssi")
        Log.i(
            TAG,
            "BLE RAW RESULT: address=$address deviceName=$deviceName rssi=$rssi " +
                "scanRecord=$rawBytes serviceUuids=$serviceUuids"
        )
        Log.i(TAG, "BLE SERVICE UUIDS: $serviceUuids")

        val matchesListed = serviceUuids.any { it == BleConstants.SERVICE_UUID }
        val matchesRaw = record?.bytes?.let { containsServiceUuid(it, BleConstants.SERVICE_UUID) } == true
        val matchesService = matchesListed || matchesRaw
        Log.i(TAG, "BLE SERVICE MATCH: $matchesService (listed=$matchesListed raw=$matchesRaw)")

        if (!matchesService) {
            Log.i(TAG, "BLE DEVICE REJECTED: $address")
            return
        }

        devicesFound++
        Log.i(TAG, "BLE DEVICE ACCEPTED: $address")
        Log.i(TAG, "BLE DEVICE FOUND: $address")
        val callback = deviceCallback
        mainHandler.post { callback?.invoke(result.device) }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(
        deviceCallback: (BluetoothDevice) -> Unit,
        errorCallback: (String) -> Unit
    ): Boolean {
        if (isScanning) {
            val message = precheckScanStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingScanPermission = false,
                scannerUnavailable = false,
                alreadyRunning = true
            )
            mainHandler.post { errorCallback(message ?: "Scan already running") }
            return true
        }

        this.deviceCallback = deviceCallback
        this.errorCallback = errorCallback

        val adapter = bluetoothAdapter
        val scanner = adapter?.bluetoothLeScanner
        val error = precheckScanStart(
            bluetoothUnavailable = adapter == null,
            bluetoothDisabled = adapter?.isEnabled != true,
            missingScanPermission = !hasScanPermission(),
            scannerUnavailable = scanner == null,
            alreadyRunning = false
        )
        if (error != null) {
            Log.e(TAG, error)
            notifyError(error, clearCallbacks = true)
            return false
        }

        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        val settings = settingsBuilder.build()

        return try {
            devicesFound = 0
            val locationMode = try {
                Settings.Secure.getInt(
                    appContext.contentResolver,
                    Settings.Secure.LOCATION_MODE
                )
            } catch (_: Exception) {
                -1
            }
            Log.i(
                TAG,
                "BLE SCAN DIAGNOSTIC: sdk=${Build.VERSION.SDK_INT} " +
                    "locationMode=$locationMode " +
                    "hasScanPermission=${hasScanPermission()} " +
                    "expectedServiceUuid=${BleConstants.SERVICE_UUID}"
            )
            if (locationMode == 0) {
                Log.e(
                    TAG,
                    "BLE SCAN WARNING: Location is OFF (locationMode=0). " +
                        "On Android 6-11, ScanCallback.onScanResult is not invoked until Location is enabled."
                )
            }
            // No hardware filter: some phones drop UUID-filtered advertisements.
            // ResQNet packets are matched in onScanResult by SERVICE_UUID.
            scanner!!.startScan(emptyList(), settings, scanCallback)
            isScanning = true
            Log.i(TAG, "BLE scan started")
            true
        } catch (e: SecurityException) {
            val message = "BLUETOOTH_SCAN permission is missing"
            Log.e(TAG, message, e)
            notifyError(message, clearCallbacks = true)
            false
        } catch (e: Exception) {
            val message = "Failed to start BLE scan: ${e.message ?: "unknown error"}"
            Log.e(TAG, message, e)
            notifyError(message, clearCallbacks = true)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (isScanning) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            Log.i(TAG, "BLE scan stopped")
        }
        isScanning = false
        clearCallbacks()
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun notifyError(message: String, clearCallbacks: Boolean) {
        val callback = errorCallback
        if (clearCallbacks) {
            this.clearCallbacks()
        }
        if (callback != null) {
            mainHandler.post { callback.invoke(message) }
        }
    }

    private fun clearCallbacks() {
        deviceCallback = null
        errorCallback = null
    }

    private fun containsServiceUuid(scanRecord: ByteArray, uuid: UUID): Boolean {
        val bigEndian = uuidToBytes(uuid)
        val littleEndian = bigEndian.reversedArray()
        return indexOf(scanRecord, littleEndian) >= 0 || indexOf(scanRecord, bigEndian) >= 0
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > haystack.size) {
            return -1
        }
        outer@ for (i in 0..(haystack.size - needle.size)) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    continue@outer
                }
            }
            return i
        }
        return -1
    }

    private fun toHex(bytes: ByteArray?): String {
        if (bytes == null) {
            return "null"
        }
        return bytes.joinToString(" ") { byte -> "%02X".format(byte) }
    }
}
