package com.resqnet.location

/**
 * Emergency / SOS location payload.
 *
 * Preserves the original SOS module fields (`type`, `latitude`, `longitude`)
 * and includes GPS accuracy and timestamp for mesh/emergency use.
 */
data class SOSData(
    val type: String = "SOS",
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun from(type: String, fix: LocationFix): SOSData = SOSData(
            type = type,
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracy = fix.accuracy,
            timestamp = fix.timestamp
        )
    }
}
