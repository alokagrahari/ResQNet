package com.resqnet.storage.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.resqnet.storage.dao.EmergencyDao
import com.resqnet.storage.entity.EmergencyEntity
import com.resqnet.storage.entity.SyncStatusConverter

@Database(
    entities = [EmergencyEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(SyncStatusConverter::class)
abstract class ResQNetDatabase : RoomDatabase() {
    abstract fun emergencyDao(): EmergencyDao

    companion object {
        private const val DB_NAME = "resqnet.db"

        @Volatile
        private var instance: ResQNetDatabase? = null

        fun getInstance(context: Context): ResQNetDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ResQNetDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
        }

        fun createInMemory(context: Context): ResQNetDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                ResQNetDatabase::class.java
            )
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        }
    }
}
