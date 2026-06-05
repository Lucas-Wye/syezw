package org.syezw.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: GpsLocation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<GpsLocation>)

    @Query("SELECT * FROM gps_locations ORDER BY timestamp DESC")
    fun getAll(): Flow<List<GpsLocation>>

    @Query("SELECT * FROM gps_locations ORDER BY timestamp DESC")
    suspend fun getAllList(): List<GpsLocation>

    @Query("SELECT * FROM gps_locations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<GpsLocation>

    @Query("SELECT * FROM gps_locations ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLocation(): GpsLocation?

    @Query("UPDATE gps_locations SET endTimestamp = :endTime WHERE id = :id")
    suspend fun updateEndTime(id: Long, endTime: Long)

    @Query("SELECT COUNT(*) FROM gps_locations")
    suspend fun count(): Int

    @Query("DELETE FROM gps_locations")
    suspend fun clearAll()
}
