package org.syezw.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gps_locations",
    indices = [Index(value = ["timestamp"])]
)
data class GpsLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val altitude: Double? = null,
    val speed: Float? = null,
    val timestamp: Long,
    val endTimestamp: Long? = null,
    val author: String = "",
    val synced: Boolean = false
)
