package com.resqnet.storage.sync

import androidx.test.core.app.ApplicationProvider
import com.resqnet.storage.api.FakeEmergencyApi
import com.resqnet.storage.database.ResQNetDatabase
import com.resqnet.storage.entity.SyncStatus
import com.resqnet.storage.repository.EmergencyRepository
import com.resqnet.storage.sampleEmergency
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncManagerTest {

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
    fun `successful sync marks record SYNCED`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-OK"))
        val api = FakeEmergencyApi(shouldSucceed = true)
        val syncManager = SyncManager(repository, api, context = null)

        syncManager.syncPending()

        assertEquals(1, api.uploaded.size)
        val uploaded = api.uploaded[0]
        assertEquals("MSG-OK", uploaded.messageId)
        assertEquals("NODE_A", uploaded.sourceNodeId)
        assertEquals("Medical", uploaded.emergencyType)
        assertEquals(28.6139, uploaded.latitude, 0.0001)
        assertEquals(77.2090, uploaded.longitude, 0.0001)
        assertEquals(1_700_000_000_000L, uploaded.timestamp)
        assertEquals(SyncStatus.SYNCED, repository.getEmergency("MSG-OK")!!.syncStatus)
    }

    @Test
    fun `failed sync keeps the record as FAILED`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-ERR"))
        val api = FakeEmergencyApi(shouldSucceed = false)
        val syncManager = SyncManager(repository, api, context = null)

        syncManager.syncPending()

        val loaded = repository.getEmergency("MSG-ERR")
        assertNotNull(loaded)
        assertEquals(SyncStatus.FAILED, loaded!!.syncStatus)
        assertEquals(1, repository.getAllEmergencies().size)
        assertTrue(api.uploaded.isEmpty())
    }

    @Test
    fun `failed record is retried on a later sync`() = runTest {
        repository.saveEmergency(sampleEmergency("MSG-RETRY"))
        val api = FakeEmergencyApi(shouldSucceed = false)
        val syncManager = SyncManager(repository, api, context = null)
        syncManager.syncPending()
        assertEquals(SyncStatus.FAILED, repository.getEmergency("MSG-RETRY")!!.syncStatus)

        api.shouldSucceed = true
        syncManager.syncPending()

        assertEquals(SyncStatus.SYNCED, repository.getEmergency("MSG-RETRY")!!.syncStatus)
        assertEquals(1, api.uploaded.size)
    }
}
