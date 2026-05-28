package org.syezw.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GpsLocationDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: GpsLocationDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.gpsLocationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve() = runBlocking {
        val ts = System.currentTimeMillis()
        val location = GpsLocation(
            latitude = 39.9042,
            longitude = 116.4074,
            accuracy = 10.0f,
            altitude = 50.0,
            speed = 1.5f,
            timestamp = ts,
            endTimestamp = ts,
            author = "test_user"
        )

        val id = dao.insert(location)
        assertTrue(id > 0)

        val all = dao.getAllList()
        assertEquals(1, all.size)
        assertEquals(39.9042, all[0].latitude, 0.0001)
        assertEquals(116.4074, all[0].longitude, 0.0001)
        assertEquals(ts, all[0].endTimestamp)
        assertEquals("test_user", all[0].author)
    }

    @Test
    fun insertMultipleAndCount() = runBlocking {
        val locations = listOf(
            GpsLocation(latitude = 39.9, longitude = 116.4, timestamp = 1000L, endTimestamp = 1000L, author = "user1"),
            GpsLocation(latitude = 40.0, longitude = 116.5, timestamp = 2000L, endTimestamp = 2000L, author = "user1"),
            GpsLocation(latitude = 40.1, longitude = 116.6, timestamp = 3000L, endTimestamp = 3000L, author = "user1")
        )

        dao.insertAll(locations)

        val count = dao.count()
        assertEquals(3, count)
    }

    @Test
    fun getByTimeRange() = runBlocking {
        val locations = listOf(
            GpsLocation(latitude = 39.9, longitude = 116.4, timestamp = 1000L, endTimestamp = 1000L),
            GpsLocation(latitude = 40.0, longitude = 116.5, timestamp = 2000L, endTimestamp = 2000L),
            GpsLocation(latitude = 40.1, longitude = 116.6, timestamp = 3000L, endTimestamp = 3000L),
            GpsLocation(latitude = 40.2, longitude = 116.7, timestamp = 4000L, endTimestamp = 4000L)
        )

        dao.insertAll(locations)

        val range = dao.getByTimeRange(1500L, 3500L)
        assertEquals(2, range.size)
        assertEquals(2000L, range[0].timestamp)
        assertEquals(3000L, range[1].timestamp)
    }

    @Test
    fun clearAll() = runBlocking {
        val locations = listOf(
            GpsLocation(latitude = 39.9, longitude = 116.4, timestamp = 1000L, endTimestamp = 1000L),
            GpsLocation(latitude = 40.0, longitude = 116.5, timestamp = 2000L, endTimestamp = 2000L)
        )

        dao.insertAll(locations)
        assertEquals(2, dao.count())

        dao.clearAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun flowEmitsData() = runBlocking {
        val ts = System.currentTimeMillis()
        val location = GpsLocation(
            latitude = 39.9042,
            longitude = 116.4074,
            timestamp = ts,
            endTimestamp = ts,
            author = "flow_test"
        )

        dao.insert(location)

        val flowData = dao.getAll().first()
        assertEquals(1, flowData.size)
        assertEquals("flow_test", flowData[0].author)
    }
}
