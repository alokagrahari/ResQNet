package com.resqnet.storage.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.resqnet.storage.repository.EmergencyRepository
import com.resqnet.storage.sampleEmergency
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EmergencyPersistenceTest {

    @Test
    fun `data remains available after database restart`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "resqnet-persist-test.db"
        context.deleteDatabase(dbName)

        val first = Room.databaseBuilder(context, ResQNetDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val firstRepo = EmergencyRepository(first.emergencyDao())
        firstRepo.saveEmergency(sampleEmergency("MSG-RESTART"))
        assertEquals(1, firstRepo.getAllEmergencies().size)
        first.close()

        val second = Room.databaseBuilder(context, ResQNetDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val secondRepo = EmergencyRepository(second.emergencyDao())
        val loaded = secondRepo.getEmergency("MSG-RESTART")
        assertNotNull(loaded)
        assertEquals("MSG-RESTART", loaded!!.messageId)
        assertEquals("NODE_A", loaded.sourceNodeId)
        assertEquals("Medical", loaded.emergencyType)
        assertEquals(28.6139, loaded.latitude, 0.0001)
        assertEquals(77.2090, loaded.longitude, 0.0001)
        assertEquals(1_700_000_000_000L, loaded.timestamp)
        second.close()
        context.deleteDatabase(dbName)
    }
}
