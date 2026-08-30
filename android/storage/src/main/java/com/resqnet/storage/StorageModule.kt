package com.resqnet.storage

import android.content.Context
import com.resqnet.storage.api.EmergencyApi
import com.resqnet.storage.api.HttpEmergencyApi
import com.resqnet.storage.database.ResQNetDatabase
import com.resqnet.storage.repository.EmergencyRepository
import com.resqnet.storage.sync.SyncManager

/**
 * App-facing entry point for Room storage and backend sync.
 */
object StorageModule {

    fun database(context: Context): ResQNetDatabase {
        return ResQNetDatabase.getInstance(context)
    }

    fun repository(context: Context): EmergencyRepository {
        return EmergencyRepository(database(context).emergencyDao())
    }

    fun emergencyApi(baseUrl: String = HttpEmergencyApi.DEFAULT_BASE_URL): EmergencyApi {
        return HttpEmergencyApi(baseUrl)
    }

    fun syncManager(
        context: Context,
        api: EmergencyApi = emergencyApi()
    ): SyncManager {
        return SyncManager(repository(context), api, context.applicationContext)
    }
}
