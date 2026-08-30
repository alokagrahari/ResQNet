package com.resqnet.storage.repository

import androidx.test.core.app.ApplicationProvider
import com.resqnet.storage.database.ResQNetDatabase
import com.resqnet.storage.entity.SyncStatus
import com.resqnet.storage.sampleEmergency
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EmergencyRepositoryTest {

    private lateinit var database: ResQNetDatabase
    private lateinit var repository: EmergencyRepository

    @Before
    fun setUp() {
        database = ResQNetDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = EmergencyRepository(database.emergencyDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `emergency can be saved locally`() = runTest {
        val saved = repository.saveEmergency(sampleEmergency("MSG-1"))
        assertTrue(saved)
        assertEquals(1, repository.getAllEmergencies().size)
    }

    @Test
    fun `emergency can be retrieved by messageId`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-2"))
        val loaded = repository.getEmergency("MSG-2")
        assertNotNull(loaded)
        assertEquals("MSG-2", loaded!!.messageId)
        assertEquals("NODE_A", loaded.sourceNodeId)
        assertEquals(28.6139, loaded.latitude, 0.0001)
        assertEquals(77.2090, loaded.longitude, 0.0001)
    }

    @Test
    fun `mesh identity fields are stored and returned unchanged`() = runTest {
        val original = sampleEmergency("MSG-IDENTITY", type = "SOS")
        assertTrue(repository.saveEmergency(original))
        val loaded = repository.getEmergency("MSG-IDENTITY")
        assertNotNull(loaded)
        assertEquals(original.messageId, loaded!!.messageId)
        assertEquals(original.sourceNodeId, loaded.sourceNodeId)
        assertEquals(original.emergencyType, loaded.emergencyType)
        assertEquals(original.latitude, loaded.latitude, 0.0001)
        assertEquals(original.longitude, loaded.longitude, 0.0001)
        assertEquals(original.timestamp, loaded.timestamp)
    }

    @Test
    fun `same messageId cannot create duplicate emergency records`() = runTest {
        assertTrue(repository.saveEmergency(sampleEmergency("MSG-DUP")))
        assertFalse(repository.saveEmergency(sampleEmergency("MSG-DUP", type = "Fire")))
        assertEquals(1, repository.getAllEmergencies().size)
        assertEquals("Medical", repository.getEmergency("MSG-DUP")!!.emergencyType)
    }

    @Test
    fun `new emergency starts as PENDING`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-P"))
        val loaded = repository.getEmergency("MSG-P")
        assertEquals(SyncStatus.PENDING, loaded!!.syncStatus)
        assertEquals(1, repository.getPendingEmergencies().size)
    }

    @Test
    fun `pending moves through SYNCING to SYNCED`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-S"))
        repository.markSyncing("MSG-S")
        assertEquals(SyncStatus.SYNCING, repository.getEmergency("MSG-S")!!.syncStatus)
        repository.markSynced("MSG-S")
        assertEquals(SyncStatus.SYNCED, repository.getEmergency("MSG-S")!!.syncStatus)
        assertTrue(repository.getPendingEmergencies().isEmpty())
    }

    @Test
    fun `failed sync does not delete the record`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-F"))
        repository.markSyncing("MSG-F")
        repository.markFailed("MSG-F")
        val loaded = repository.getEmergency("MSG-F")
        assertNotNull(loaded)
        assertEquals(SyncStatus.FAILED, loaded!!.syncStatus)
        assertEquals(1, repository.getAllEmergencies().size)
    }

    @Test
    fun `multiple independent emergencies can be stored`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-A"))
        repository.saveEmergency(sampleEmergency("MSG-B", type = "Fire"))
        repository.saveEmergency(sampleEmergency("MSG-C", type = "Flood"))
        assertEquals(3, repository.getAllEmergencies().size)
        assertNotNull(repository.getEmergency("MSG-A"))
        assertNotNull(repository.getEmergency("MSG-B"))
        assertNotNull(repository.getEmergency("MSG-C"))
        assertNull(repository.getEmergency("MSG-MISSING"))
    }
}
