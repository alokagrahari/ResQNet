package com.resqnet.storage.repository

import com.resqnet.storage.entity.SyncStatus

/**
 * Public storage model. Mapped from mesh [Packet] + location SOS fields by the
 * app coordinator — Storage does not depend on mesh or location modules.
 */
data class EmergencyRecord(
    val messageId: String,
    val sourceNodeId: String,
    val destinationNodeId: String,
    val payload: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val ttl: Int,
    val hopCount: Int,
    val timestamp: Long,
    val emergencyType: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val id: String get() = messageId
}
