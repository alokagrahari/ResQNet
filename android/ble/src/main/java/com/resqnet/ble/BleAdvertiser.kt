package com.resqnet.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat

class BleAdvertiser(context: Context) {

    companion object {
        private const val TAG = "ResQNetBLE"

        fun mapAdvertiseError(errorCode: Int): String = when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ->
                "Advertise data is too large"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
                "Too many BLE advertisers are already running"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED ->
                "Advertising already started"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR ->
                "BLE advertising internal error"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
                "This phone does not support BLE peripheral advertising"
            else ->
                "BLE advertising failed with error code $errorCode"
        }

        fun precheckAdvertiseStart(
            bluetoothUnavailable: Boolean,
            bluetoothDisabled: Boolean,
            missingAdvertisePermission: Boolean,
            advertiserUnavailable: Boolean,
            peripheralAdvertisingUnsupported: Boolean,
            alreadyStarted: Boolean
        ): String? = when {
            alreadyStarted -> "Advertising already started"
            bluetoothUnavailable -> "Bluetooth is unavailable on this device"
            bluetoothDisabled -> "Bluetooth is disabled"
            missingAdvertisePermission -> "BLUETOOTH_ADVERTISE permission is missing"
            advertiserUnavailable -> "BLE advertiser is unavailable"
            peripheralAdvertisingUnsupported ->
                "This phone does not support BLE peripheral advertising"
            else -> null
        }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var isAdvertising = false
    private var successCallback: (() -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            isAdvertising = true
            Log.i(TAG, "BLE advertising started")
            val callback = successCallback
            successCallback = null
            if (callback != null) {
                mainHandler.post { callback.invoke() }
            }
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            isAdvertising = false
            val message = mapAdvertiseError(errorCode)
            Log.e(TAG, message)
            notifyError(message, clearCallbacks = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(
        successCallback: () -> Unit,
        errorCallback: (String) -> Unit
    ): Boolean {
        if (isAdvertising) {
            val message = precheckAdvertiseStart(
                bluetoothUnavailable = false,
                bluetoothDisabled = false,
                missingAdvertisePermission = false,
                advertiserUnavailable = false,
                peripheralAdvertisingUnsupported = false,
                alreadyStarted = true
            )
            mainHandler.post { errorCallback(message ?: "Advertising already started") }
            return true
        }

        this.successCallback = successCallback
        this.errorCallback = errorCallback

        val adapter = bluetoothAdapter
        val bleFeature = appContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val multipleAdvertisementSupported = try {
            adapter?.isMultipleAdvertisementSupported
        } catch (e: Exception) {
            Log.e(TAG, "isMultipleAdvertisementSupported threw", e)
            null
        }
        val advertiser = try {
            adapter?.bluetoothLeAdvertiser
        } catch (e: Exception) {
            Log.e(TAG, "bluetoothLeAdvertiser threw", e)
            null
        }
        val adapterName = try {
            adapter?.name ?: "<null>"
        } catch (_: SecurityException) {
            "<permission denied>"
        } catch (_: Exception) {
            "<unavailable>"
        }

        // isMultipleAdvertisementSupported=false only means concurrent ads are unsupported.
        // Legacy single-advertisement can still work if bluetoothLeAdvertiser is non-null.
        Log.i(
            TAG,
            """
            BLE DIAGNOSTIC:
            bluetoothAdapterExists = ${adapter != null}
            bluetoothEnabled = ${adapter?.isEnabled == true}
            bleFeature = $bleFeature
            multipleAdvertisementSupported = $multipleAdvertisementSupported
            advertiserNull = ${advertiser == null}
            sdk = ${Build.VERSION.SDK_INT}
            adapterName = $adapterName
            """.trimIndent()
        )

        val error = precheckAdvertiseStart(
            bluetoothUnavailable = adapter == null,
            bluetoothDisabled = adapter?.isEnabled != true,
            missingAdvertisePermission = !hasAdvertisePermission(),
            advertiserUnavailable = advertiser == null,
            peripheralAdvertisingUnsupported = false,
            alreadyStarted = false
        )
        if (error != null) {
            val detailedError = if (error == "BLE advertiser is unavailable") {
                "BLE advertiser is unavailable: bluetoothLeAdvertiser is null " +
                    "(sdk=${Build.VERSION.SDK_INT}, " +
                    "bleFeature=$bleFeature, " +
                    "multipleAdvertisementSupported=$multipleAdvertisementSupported, " +
                    "bluetoothEnabled=${adapter?.isEnabled == true})"
            } else {
                error
            }
            Log.e(TAG, detailedError)
            notifyError(detailedError, clearCallbacks = true)
            return false
        }

        if (multipleAdvertisementSupported != true) {
            Log.w(
                TAG,
                "isMultipleAdvertisementSupported=$multipleAdvertisementSupported; " +
                    "attempting legacy single BLE advertisement via bluetoothLeAdvertiser"
            )
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        Log.i(
            TAG,
            "BLE ADVERTISE DATA: serviceUuid=${BleConstants.SERVICE_UUID} " +
                "advertiseServiceUuids=${advertiseData.serviceUuids} " +
                "scanResponseServiceUuids=${scanResponse.serviceUuids}"
        )

        return try {
            advertiser!!.startAdvertising(
                settings,
                advertiseData,
                scanResponse,
                advertiseCallback
            )
            isAdvertising = true
            Log.i(TAG, "BLE advertising start requested")
            true
        } catch (e: SecurityException) {
            isAdvertising = false
            val message = "BLUETOOTH_ADVERTISE permission is missing"
            Log.e(TAG, message, e)
            notifyError(message, clearCallbacks = true)
            false
        } catch (e: Exception) {
            isAdvertising = false
            val message = "Failed to start BLE advertising: ${e.message ?: "unknown error"}"
            Log.e(TAG, message, e)
            notifyError(message, clearCallbacks = true)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (isAdvertising) {
            try {
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            Log.d(TAG, "BLE advertising stopped")
        }
        isAdvertising = false
        clearCallbacks()
    }

    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
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
        successCallback = null
        errorCallback = null
    }
}
