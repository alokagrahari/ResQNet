package com.resqnet.storage.api

import com.resqnet.storage.repository.EmergencyRecord

/**
 * HTTP upload of a locally stored emergency. Implementations must not talk to
 * MongoDB directly — only the Node.js API.
 */
interface EmergencyApi {
    suspend fun uploadEmergency(record: EmergencyRecord): Result<Unit>
}
