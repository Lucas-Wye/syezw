package org.syezw.sync

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class GpsSyncTest {

    @Test
    fun gpsPayload_roundTripsThroughJson() {
        val gson = GsonBuilder().create()
        val payload = GpsPayload(
            latitude = 39.9042,
            longitude = 116.4074,
            accuracy = 10.0f,
            altitude = 50.0,
            speed = 1.5f,
            timestamp = 1_717_000_000_000L,
            endTimestamp = 1_717_000_000_500L,
            author = "tester"
        )

        val parsed = gson.fromJson(gson.toJson(payload), GpsPayload::class.java)
        assertEquals(payload.latitude, parsed.latitude, 0.0001)
        assertEquals(payload.longitude, parsed.longitude, 0.0001)
        assertEquals(payload.accuracy, parsed.accuracy)
        assertEquals(payload.altitude, parsed.altitude)
        assertEquals(payload.speed, parsed.speed)
        assertEquals(payload.timestamp, parsed.timestamp)
        assertEquals(payload.endTimestamp, parsed.endTimestamp)
        assertEquals(payload.author, parsed.author)
    }
}
