package org.syezw.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.syezw.MainActivity
import org.syezw.data.AppDatabase
import org.syezw.data.GpsLocation
import org.syezw.dataStore
import org.syezw.util.GpsDistanceUtils

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: AppDatabase? = null
    private var gpsCheckTimer: kotlinx.coroutines.Job? = null

    companion object {
        private const val TAG = "LocationService"
        const val CHANNEL_ID = "gps_location_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "org.syezw.action.STOP_LOCATION_SERVICE"

        const val EXTRA_PRIORITY = "location_priority"
        const val EXTRA_INTERVAL_MS = "location_interval_ms"
        const val EXTRA_FASTEST_INTERVAL_MS = "location_fastest_interval_ms"
        const val EXTRA_AUTHOR = "location_author"

        const val DEFAULT_INTERVAL_MS = 10_000L
        const val DEFAULT_FASTEST_INTERVAL_MS = 5_000L
        const val MIN_INTERVAL_MS = 5_000L
        const val DEFAULT_PRIORITY = Priority.PRIORITY_BALANCED_POWER_ACCURACY

        private val GPS_PRIORITY_KEY = stringPreferencesKey("gps_priority")
        private val GPS_INTERVAL_MS_KEY = stringPreferencesKey("gps_interval_ms")
        private val GPS_FASTEST_INTERVAL_MS_KEY = stringPreferencesKey("gps_fastest_interval_ms")
        private val DEFAULT_AUTHOR_KEY = stringPreferencesKey("default_author")

        fun start(
            context: Context,
            priority: Int = DEFAULT_PRIORITY,
            intervalMs: Long = DEFAULT_INTERVAL_MS,
            fastestIntervalMs: Long = DEFAULT_FASTEST_INTERVAL_MS,
            author: String = ""
        ) {
            val intent = Intent(context, LocationService::class.java).apply {
                putExtra(EXTRA_PRIORITY, priority)
                putExtra(EXTRA_INTERVAL_MS, intervalMs)
                putExtra(EXTRA_FASTEST_INTERVAL_MS, fastestIntervalMs)
                putExtra(EXTRA_AUTHOR, author)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val priority: Int
        val intervalMs: Long
        val fastestIntervalMs: Long
        val author: String

        if (intent != null && intent.hasExtra(EXTRA_PRIORITY)) {
            priority = intent.getIntExtra(EXTRA_PRIORITY, DEFAULT_PRIORITY)
            intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS)
                .coerceAtLeast(MIN_INTERVAL_MS)
            fastestIntervalMs = intent.getLongExtra(EXTRA_FASTEST_INTERVAL_MS, DEFAULT_FASTEST_INTERVAL_MS)
                .coerceAtLeast(MIN_INTERVAL_MS)
            author = intent.getStringExtra(EXTRA_AUTHOR) ?: ""
        } else {
            val prefs = runCatching {
                runBlockingReadPrefs()
            }.getOrDefault(null)
            priority = prefs?.priority ?: DEFAULT_PRIORITY
            intervalMs = (prefs?.intervalMs ?: DEFAULT_INTERVAL_MS).coerceAtLeast(MIN_INTERVAL_MS)
            fastestIntervalMs = (prefs?.fastestIntervalMs ?: DEFAULT_FASTEST_INTERVAL_MS).coerceAtLeast(MIN_INTERVAL_MS)
            author = prefs?.author ?: ""
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        database = AppDatabase.getDatabase(this)

        startLocationUpdates(priority, intervalMs, fastestIntervalMs, author)
        startGpsStatusCheck()

        return START_STICKY
    }

    private data class RestoredPrefs(
        val priority: Int,
        val intervalMs: Long,
        val fastestIntervalMs: Long,
        val author: String
    )

    private fun runBlockingReadPrefs(): RestoredPrefs? {
        val prefs = runCatching {
            kotlinx.coroutines.runBlocking {
                dataStore.data.first()
            }
        }.getOrNull() ?: return null

        val priorityStr = prefs[GPS_PRIORITY_KEY] ?: "balanced"
        val priority = when (priorityStr) {
            "high_accuracy" -> Priority.PRIORITY_HIGH_ACCURACY
            "balanced" -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            "low_power" -> Priority.PRIORITY_LOW_POWER
            "no_power" -> Priority.PRIORITY_PASSIVE
            else -> DEFAULT_PRIORITY
        }
        val intervalMs = prefs[GPS_INTERVAL_MS_KEY]?.toLongOrNull() ?: DEFAULT_INTERVAL_MS
        val fastestIntervalMs = prefs[GPS_FASTEST_INTERVAL_MS_KEY]?.toLongOrNull() ?: DEFAULT_FASTEST_INTERVAL_MS
        val author = prefs[DEFAULT_AUTHOR_KEY] ?: ""

        return RestoredPrefs(priority, intervalMs, fastestIntervalMs, author)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        stopGpsStatusCheck()
        serviceScope.cancel()
    }

    private fun isSystemLocationEnabled(): Boolean {
        return try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check location enabled status", e)
            false
        }
    }

    private fun startGpsStatusCheck() {
        gpsCheckTimer?.cancel()
        gpsCheckTimer = serviceScope.launch {
            while (true) {
                delay(30_000)
                if (!isSystemLocationEnabled()) {
                    Log.w(TAG, "System location service disabled, stopping GPS tracking")
                    stopSelf()
                    break
                }
            }
        }
    }

    private fun stopGpsStatusCheck() {
        gpsCheckTimer?.cancel()
        gpsCheckTimer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for background GPS location tracking"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startLocationUpdates(priority: Int, intervalMs: Long, fastestIntervalMs: Long, author: String) {
        val locationRequest = LocationRequest.Builder(intervalMs)
            .setMinUpdateIntervalMillis(fastestIntervalMs)
            .setPriority(priority)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    saveLocation(location, author)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission, stopping service", e)
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        if (!::locationCallback.isInitialized) {
            return
        }
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to remove location updates due to missing permission", e)
        }
    }

    private fun saveLocation(location: Location, author: String) {
        val db = database
        if (db == null) {
            Log.w(TAG, "Database unavailable, dropping location update")
            return
        }
        serviceScope.launch {
            try {
                val lastLocation = db.gpsLocationDao().getLastLocation()

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
                        return@launch
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
                db.gpsLocationDao().insert(gpsLocation)
            } catch (e: SQLiteException) {
                Log.e(TAG, "Failed to persist GPS location", e)
            }
        }
    }
}
