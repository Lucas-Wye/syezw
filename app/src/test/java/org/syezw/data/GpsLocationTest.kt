package org.syezw.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsLocationTest {

    @Test
    fun gpsLocation_creation() {
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

        assertEquals(39.9042, location.latitude, 0.0001)
        assertEquals(116.4074, location.longitude, 0.0001)
        assertEquals(10.0f, location.accuracy)
        assertEquals(50.0, location.altitude)
        assertEquals(1.5f, location.speed)
        assertEquals(ts, location.endTimestamp)
        assertEquals("test_user", location.author)
        assertEquals(0L, location.id)
    }

    @Test
    fun gpsLocation_defaultValues() {
        val location = GpsLocation(
            latitude = 0.0,
            longitude = 0.0,
            timestamp = 0L
        )

        assertEquals(0L, location.id)
        assertEquals("", location.author)
        assertEquals(null, location.accuracy)
        assertEquals(null, location.altitude)
        assertEquals(null, location.speed)
        assertEquals(null, location.endTimestamp)
    }

    @Test
    fun gpsLocation_equality() {
        val loc1 = GpsLocation(
            id = 1,
            latitude = 39.9,
            longitude = 116.4,
            timestamp = 1000L,
            author = "user"
        )
        val loc2 = GpsLocation(
            id = 1,
            latitude = 39.9,
            longitude = 116.4,
            timestamp = 1000L,
            author = "user"
        )

        assertEquals(loc1, loc2)
        assertEquals(loc1.hashCode(), loc2.hashCode())
    }
}
