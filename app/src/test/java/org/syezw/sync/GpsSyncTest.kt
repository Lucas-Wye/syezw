package org.syezw.sync

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.syezw.data.GpsLocation

class GpsSyncTest {

    @Test
    fun buildGpsSyncItem_encryptsPayloadAndHashes() {
        val ts = 1_717_000_000_000L
        val location = GpsLocation(
            id = 1,
            latitude = 39.9042,
            longitude = 116.4074,
            accuracy = 10.0f,
            altitude = 50.0,
            speed = 1.5f,
            timestamp = ts,
            endTimestamp = ts,
            author = "tester"
        )
        val gson = GsonBuilder().create()
        val key = deriveAesKeyFromPassphrase("passphrase")

        val item = buildGpsSyncItem(location, gson, key, "fallback")
        val expectedPayload = GpsPayload(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            altitude = location.altitude,
            speed = location.speed,
            timestamp = location.timestamp,
            endTimestamp = location.endTimestamp,
            author = location.author
        )
        val payloadJson = gson.toJson(expectedPayload)

        assertEquals(sha256Hex(payloadJson.toByteArray(Charsets.UTF_8)), item.uuid)
        assertEquals(location.author, item.author)
        assertEquals(location.timestamp, item.timestamp)

        val decrypted = String(decryptFromBlob(item.payload, key), Charsets.UTF_8)
        val parsed = gson.fromJson(decrypted, GpsPayload::class.java)
        assertEquals(location.latitude, parsed.latitude, 0.0001)
        assertEquals(location.longitude, parsed.longitude, 0.0001)
        assertEquals(location.accuracy, parsed.accuracy)
        assertEquals(location.altitude, parsed.altitude)
        assertEquals(location.speed, parsed.speed)
        assertEquals(location.timestamp, parsed.timestamp)
        assertEquals(location.endTimestamp, parsed.endTimestamp)
        assertEquals(location.author, parsed.author)
    }
}
