package com.resqnet.mesh

import java.util.Locale

/**
 * Demo emergency payload carried inside an unchanged MESH:v1 packet.
 *
 * Format (payload only; packet fields are untouched):
 * `EMERGENCY:SOS|TYPE=...|PRIORITY=...|LAT=...|LON=...|MSG=...`
 *
 * Non-emergency payloads (hello-mesh, JSON SOS, etc.) parse as null.
 */
data class EmergencySos(
    val type: String,
    val priority: String,
    val latitude: Double?,
    val longitude: Double?,
    val message: String,
    val latitudeValid: Boolean,
    val longitudeValid: Boolean,
    val hasLatitude: Boolean,
    val hasLongitude: Boolean
) {
    val hasValidCoordinates: Boolean get() = latitudeValid && longitudeValid
}

object EmergencyPayload {
    const val PREFIX = "EMERGENCY:SOS"
    const val DEFAULT_TYPE = "MEDICAL"
    const val DEFAULT_PRIORITY = "CRITICAL"
    const val DEFAULT_MESSAGE = "Immediate assistance required"

    fun encode(
        latitude: Double,
        longitude: Double,
        type: String = DEFAULT_TYPE,
        priority: String = DEFAULT_PRIORITY,
        message: String = DEFAULT_MESSAGE
    ): String {
        return PREFIX +
            "|TYPE=$type" +
            "|PRIORITY=$priority" +
            "|LAT=${formatCoord(latitude)}" +
            "|LON=${formatCoord(longitude)}" +
            "|MSG=$message"
    }

    fun parse(payload: String): EmergencySos? {
        if (!payload.startsWith(PREFIX)) {
            return null
        }
        val fields = linkedMapOf<String, String>()
        payload.split("|").drop(1).forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) {
                fields[part.substring(0, eq)] = part.substring(eq + 1)
            }
        }
        val latRaw = fields["LAT"]
        val lonRaw = fields["LON"]
        val lat = latRaw?.toDoubleOrNull()
        val lon = lonRaw?.toDoubleOrNull()
        val latValid = lat != null && lat in -90.0..90.0
        val lonValid = lon != null && lon in -180.0..180.0
        return EmergencySos(
            type = fields["TYPE"].orEmpty(),
            priority = fields["PRIORITY"].orEmpty(),
            latitude = if (latValid) lat else null,
            longitude = if (lonValid) lon else null,
            message = fields["MSG"].orEmpty(),
            latitudeValid = latValid,
            longitudeValid = lonValid,
            hasLatitude = !latRaw.isNullOrBlank(),
            hasLongitude = !lonRaw.isNullOrBlank()
        )
    }

    fun formatCoord(value: Double): String = String.format(Locale.US, "%.6f", value)
}
