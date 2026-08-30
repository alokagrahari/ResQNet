package com.resqnet.storage.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.resqnet.storage.api.EmergencyApi
import com.resqnet.storage.repository.EmergencyRepository

/**
 * Pushes PENDING/FAILED local emergencies to the Node.js API.
 * Never talks to MongoDB. Leaves rows in Room if upload fails.
 */
class SyncManager(
    private val repository: EmergencyRepository,
    private val api: EmergencyApi,
    private val context: Context? = null
) {

    suspend fun syncPending() {
        if (context != null && !isOnline(context)) {
            return
        }
        val records = repository.getEmergenciesNeedingSync()
        for (record in records) {
            repository.markSyncing(record.messageId)
            val result = api.uploadEmergency(record)
            if (result.isSuccess) {
                repository.markSynced(record.messageId)
            } else {
                repository.markFailed(record.messageId)
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            manager.activeNetworkInfo?.isConnected == true
        }
    }
}
