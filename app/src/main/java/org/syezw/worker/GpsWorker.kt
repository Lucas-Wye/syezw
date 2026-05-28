package org.syezw.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.syezw.data.AppDatabase
import org.syezw.data.GpsLocation
import org.syezw.dataStore
import org.syezw.util.GpsDistanceUtils
import java.util.concurrent.TimeUnit

class GpsWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "GpsWorker"
        const val WORKER_TAG = "gps_location_worker"
        private val DEFAULT_AUTHOR_KEY = stringPreferencesKey("default_author")
    }

    override suspend fun doWork(): Result {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted, cancelling GPS work")
            return Result.failure()
        }

        return try {
            // Log.d(TAG, "Starting GPS work")
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val database = AppDatabase.getDatabase(context)

            val location = withContext(Dispatchers.IO) {
                try {
                    Tasks.await(
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null),
                        30, TimeUnit.SECONDS
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get current location", e)
                    null
                }
            }

            if (location != null) {
                // Log.d(TAG, "Got location: %.2f, %.2f".format(location.latitude, location.longitude))
                val author = readAuthor()
                saveLocation(database, location, author)
                Result.success()
            } else {
                Log.w(TAG, "Failed to get current location")
                Result.retry()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error in GPS worker", e)
            Result.retry()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun readAuthor(): String {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[DEFAULT_AUTHOR_KEY] ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read author from DataStore", e)
            ""
        }
    }

    private suspend fun saveLocation(database: AppDatabase, location: Location, author: String) {
        try {
            val lastLocation = database.gpsLocationDao().getLastLocation()
            
            if (lastLocation != null) {
                val distance = GpsDistanceUtils.haversineDistanceMeters(
                    lastLocation.latitude, lastLocation.longitude,
                    location.latitude, location.longitude
                )
                if (distance < GpsDistanceUtils.DEFAULT_DISTANCE_THRESHOLD_M) {
                    // Log.d(
                    //     TAG,
                    //     "Location change is less than ${GpsDistanceUtils.DEFAULT_DISTANCE_THRESHOLD_M}m (${distance}m), skipping save"
                    // )
                    return
                }
            }

            val gpsLocation = GpsLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                timestamp = location.time,
                endTimestamp = location.time,
                author = author
            )
            database.gpsLocationDao().insert(gpsLocation)
            // Log.d(TAG, "Location saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save location", e)
            throw e
        }
    }
}
