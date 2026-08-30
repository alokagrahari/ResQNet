package com.resqnet.storage.api

import com.resqnet.storage.repository.EmergencyRecord

class FakeEmergencyApi(
    var shouldSucceed: Boolean = true
) : EmergencyApi {
    val uploaded = mutableListOf<EmergencyRecord>()

    override suspend fun uploadEmergency(record: EmergencyRecord): Result<Unit> {
        return if (shouldSucceed) {
            uploaded += record
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("upload failed"))
        }
    }
}
