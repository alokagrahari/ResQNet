package com.resqnet.storage.repository

import com.resqnet.storage.dao.EmergencyDao
import com.resqnet.storage.entity.EmergencyEntity
import com.resqnet.storage.entity.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EmergencyRepository(private val dao: EmergencyDao) {

    suspend fun saveEmergency(record: EmergencyRecord): Boolean {
        val existing = dao.getByMessageId(record.messageId)
        if (existing != null) {
            return false
        }
        val now = System.currentTimeMillis()
        val inserted = dao.insert(
            record.toEntity(
                syncStatus = SyncStatus.PENDING,
                createdAt = if (record.createdAt == 0L) now else record.createdAt,
                updatedAt = now
            )
        )
        return inserted != -1L
    }

    suspend fun getEmergency(messageId: String): EmergencyRecord? {
        return dao.getByMessageId(messageId)?.toRecord()
    }

    suspend fun getAllEmergencies(): List<EmergencyRecord> {
        return dao.getAll().map { it.toRecord() }
    }

    fun observeAllEmergencies(): Flow<List<EmergencyRecord>> {
        return dao.observeAll().map { list -> list.map { it.toRecord() } }
    }

    suspend fun getPendingEmergencies(): List<EmergencyRecord> {
        return dao.getByStatus(SyncStatus.PENDING).map { it.toRecord() }
    }

    suspend fun getEmergenciesNeedingSync(): List<EmergencyRecord> {
        return dao.getByStatuses(listOf(SyncStatus.PENDING, SyncStatus.FAILED))
            .map { it.toRecord() }
    }

    suspend fun markSyncing(messageId: String) {
        dao.updateSyncStatus(messageId, SyncStatus.SYNCING, System.currentTimeMillis())
    }

    suspend fun markSynced(messageId: String) {
        dao.updateSyncStatus(messageId, SyncStatus.SYNCED, System.currentTimeMillis())
    }

    suspend fun markFailed(messageId: String) {
        dao.updateSyncStatus(messageId, SyncStatus.FAILED, System.currentTimeMillis())
    }

    private fun EmergencyRecord.toEntity(
        syncStatus: SyncStatus,
        createdAt: Long,
        updatedAt: Long
    ): EmergencyEntity = EmergencyEntity(
        messageId = messageId,
        sourceNodeId = sourceNodeId,
        destinationNodeId = destinationNodeId,
        payload = payload,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        ttl = ttl,
        hopCount = hopCount,
        timestamp = timestamp,
        emergencyType = emergencyType,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun EmergencyEntity.toRecord(): EmergencyRecord = EmergencyRecord(
        messageId = messageId,
        sourceNodeId = sourceNodeId,
        destinationNodeId = destinationNodeId,
        payload = payload,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        ttl = ttl,
        hopCount = hopCount,
        timestamp = timestamp,
        emergencyType = emergencyType,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
