package org.syezw.sync

import com.google.gson.Gson
import org.syezw.data.GpsLocation
import javax.crypto.spec.SecretKeySpec

fun buildGpsSyncItem(
    location: GpsLocation,
    gson: Gson,
    key: SecretKeySpec,
    fallbackAuthor: String = ""
): GpsSyncItem {
    val resolvedAuthor = if (location.author.isNotBlank()) location.author else fallbackAuthor
    val payload = GpsPayload(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracy = location.accuracy,
        altitude = location.altitude,
        speed = location.speed,
        timestamp = location.timestamp,
        endTimestamp = location.endTimestamp,
        author = resolvedAuthor
    )
    val payloadJson = gson.toJson(payload)
    val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
    return GpsSyncItem(
        uuid = sha256Hex(payloadBytes),
        author = resolvedAuthor,
        timestamp = location.timestamp,
        endTimestamp = location.endTimestamp,
        payload = encryptToBlob(payloadBytes, key)
    )
}
