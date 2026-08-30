package com.resqnet.ble

import android.content.Context
import java.util.UUID

class BleNodeId(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getOrCreate(): String {
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val id = newStableId()
        prefs.edit().putString(KEY, id).apply()
        return id
    }

    companion object {
        private const val PREFS_NAME = "resqnet_ble"
        private const val KEY = "local_node_id"

        fun newStableId(): String {
            return "n" + UUID.randomUUID().toString().replace("-", "").take(8)
        }
    }
}
