package com.resqnet.location

/**
 * GPS reading produced by [LocationHelper].
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val isLastKnown: Boolean = false
)
