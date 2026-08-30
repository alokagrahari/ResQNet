package com.resqnet.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resqnet.storage.entity.EmergencyEntity
import com.resqnet.storage.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: EmergencyEntity): Long

    @Update
    suspend fun update(entity: EmergencyEntity)

    @Query("SELECT * FROM emergencies WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): EmergencyEntity?

    @Query("SELECT * FROM emergencies ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EmergencyEntity>>

    @Query("SELECT * FROM emergencies ORDER BY createdAt DESC")
    suspend fun getAll(): List<EmergencyEntity>

    @Query("SELECT * FROM emergencies WHERE syncStatus = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(status: SyncStatus): List<EmergencyEntity>

    @Query("SELECT * FROM emergencies WHERE syncStatus IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getByStatuses(statuses: List<SyncStatus>): List<EmergencyEntity>

    @Query(
        "UPDATE emergencies SET syncStatus = :status, updatedAt = :updatedAt WHERE messageId = :messageId"
    )
    suspend fun updateSyncStatus(messageId: String, status: SyncStatus, updatedAt: Long)

    @Query("DELETE FROM emergencies WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String)
}
