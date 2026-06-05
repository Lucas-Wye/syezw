package org.syezw.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.syezw.data.GpsLocation
import org.syezw.data.GpsLocationDao

class GpsLocationSaverTest {

    @Test
    fun saveLocation_updatesEndTime_whenReadingIsNearPreviousPoint() = runBlocking {
        val dao = FakeGpsLocationDao(
            initialLocations = listOf(
                GpsLocation(
                    id = 1,
                    latitude = 39.9042,
                    longitude = 116.4074,
                    timestamp = 1_000L,
                    endTimestamp = 1_000L,
                    author = "alice"
                )
            )
        )

        GpsLocationSaver.saveLocation(
            dao = dao,
            sample = GpsLocationSample(
                latitude = 39.9042001,
                longitude = 116.4074001,
                timestamp = 2_000L
            ),
            author = "alice"
        )

        assertEquals(0, dao.insertedLocations.size)
        assertEquals(1L to 2_000L, dao.updatedEndTime)
        assertEquals(2_000L, dao.getLastLocation()?.endTimestamp)
    }

    @Test
    fun saveLocation_keepsEndTimeMonotonic_whenNearPointIsOlder() = runBlocking {
        val dao = FakeGpsLocationDao(
            initialLocations = listOf(
                GpsLocation(
                    id = 1,
                    latitude = 39.9042,
                    longitude = 116.4074,
                    timestamp = 2_000L,
                    endTimestamp = 2_500L,
                    author = "alice"
                )
            )
        )

        GpsLocationSaver.saveLocation(
            dao = dao,
            sample = GpsLocationSample(
                latitude = 39.9042001,
                longitude = 116.4074001,
                timestamp = 2_100L
            ),
            author = "alice"
        )

        assertEquals(0, dao.insertedLocations.size)
        assertEquals(1L to 2_500L, dao.updatedEndTime)
        assertEquals(2_500L, dao.getLastLocation()?.endTimestamp)
    }

    @Test
    fun saveLocation_insertsNewLocation_whenReadingIsFarAway() = runBlocking {
        val dao = FakeGpsLocationDao(
            initialLocations = listOf(
                GpsLocation(
                    id = 1,
                    latitude = 39.9042,
                    longitude = 116.4074,
                    timestamp = 1_000L,
                    endTimestamp = 1_000L,
                    author = "alice"
                )
            )
        )

        GpsLocationSaver.saveLocation(
            dao = dao,
            sample = GpsLocationSample(
                latitude = 40.0,
                longitude = 117.0,
                timestamp = 2_000L
            ),
            author = "bob"
        )

        assertEquals(1, dao.insertedLocations.size)
        assertNull(dao.updatedEndTime)
        assertEquals(2_000L, dao.insertedLocations[0].endTimestamp)
        assertEquals("bob", dao.insertedLocations[0].author)
    }

    private class FakeGpsLocationDao(
        initialLocations: List<GpsLocation> = emptyList()
    ) : GpsLocationDao {
        private var nextId = 1L
        private val locations = initialLocations.map { location ->
            if (location.id > nextId) {
                nextId = location.id
            }
            location
        }.toMutableList()
        val insertedLocations = mutableListOf<GpsLocation>()
        var updatedEndTime: Pair<Long, Long>? = null

        override suspend fun insert(location: GpsLocation): Long {
            val stored = if (location.id == 0L) {
                location.copy(id = ++nextId)
            } else {
                nextId = maxOf(nextId, location.id)
                location
            }
            locations += stored
            insertedLocations += stored
            return stored.id
        }

        override suspend fun insertAll(locations: List<GpsLocation>) {
            locations.forEach { insert(it) }
        }

        override fun getAll(): Flow<List<GpsLocation>> = flowOf(locations.sortedByDescending { it.timestamp })

        override suspend fun getAllList(): List<GpsLocation> = locations.sortedByDescending { it.timestamp }

        override suspend fun getByTimeRange(startTime: Long, endTime: Long): List<GpsLocation> {
            return locations.filter { it.timestamp in startTime..endTime }.sortedBy { it.timestamp }
        }

        override suspend fun getLastLocation(): GpsLocation? {
            return locations.maxByOrNull { it.timestamp }
        }

        override suspend fun updateEndTime(id: Long, endTime: Long) {
            updatedEndTime = id to endTime
            val index = locations.indexOfFirst { it.id == id }
            if (index >= 0) {
                locations[index] = locations[index].copy(endTimestamp = endTime)
            }
        }
    }
}
