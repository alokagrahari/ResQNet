package com.resqnet.storage

import com.resqnet.storage.repository.EmergencyRecord
import com.resqnet.storage.entity.SyncStatus

internal fun sampleEmergency(
    messageId: String,
    type: String = "Medical"
): EmergencyRecord = EmergencyRecord(
    messageId = messageId,
    sourceNodeId = "NODE_A",
    destinationNodeId = "*",
    payload = """{"type":"$type"}""",
    latitude = 28.6139,
    longitude = 77.2090,
    accuracy = 12f,
    ttl = 5,
    hopCount = 0,
    timestamp = 1_700_000_000_000L,
    emergencyType = type,
    syncStatus = SyncStatus.PENDING
)
