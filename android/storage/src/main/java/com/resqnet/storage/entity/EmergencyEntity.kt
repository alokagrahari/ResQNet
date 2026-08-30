package com.resqnet.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for an SOS / emergency. [messageId] is the unique key so the same
 * mesh packet cannot be stored twice. This does not replace Mesh engine
 * in-memory deduplication.
 */
@Entity(tableName = "emergencies")
data class EmergencyEntity(
    @PrimaryKey
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
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    val id: String get() = messageId
}
