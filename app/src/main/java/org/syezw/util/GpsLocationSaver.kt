package org.syezw.util

import android.location.Location
import org.syezw.data.GpsLocation
import org.syezw.data.GpsLocationDao

data class GpsLocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val timestamp: Long
)

fun Location.toGpsLocationSample(): GpsLocationSample {
    return GpsLocationSample(
        latitude = latitude,
        longitude = longitude,
        accuracy = if (hasAccuracy()) accuracy else null,
        altitude = if (hasAltitude()) altitude else null,
        speed = if (hasSpeed()) speed else null,
        timestamp = time
    )
}

object GpsLocationSaver {

    suspend fun saveLocation(
        dao: GpsLocationDao,
        sample: GpsLocationSample,
        author: String
    ) {
        val lastLocation = dao.getLastLocation()

        if (lastLocation != null) {
            val distance = GpsDistanceUtils.haversineDistanceMeters(
                lastLocation.latitude,
                lastLocation.longitude,
                sample.latitude,
                sample.longitude
            )
            if (distance < GpsDistanceUtils.DEFAULT_DISTANCE_THRESHOLD_M) {
                val nextEndTimestamp = maxOf(lastLocation.endTimestamp ?: lastLocation.timestamp, sample.timestamp)
                dao.updateEndTime(lastLocation.id, nextEndTimestamp)
                return
            }
        }

        dao.insert(
            GpsLocation(
                latitude = sample.latitude,
                longitude = sample.longitude,
                accuracy = sample.accuracy,
                altitude = sample.altitude,
                speed = sample.speed,
                timestamp = sample.timestamp,
                endTimestamp = sample.timestamp,
                author = author
            )
        )
    }
}
