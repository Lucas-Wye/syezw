package org.syezw.model

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object GpsPrefKeys {
    val GPS_ENABLED = booleanPreferencesKey("gps_enabled")
    val GPS_PRIORITY = stringPreferencesKey("gps_priority")
    val GPS_INTERVAL_MS = stringPreferencesKey("gps_interval_ms")
    val GPS_FASTEST_INTERVAL_MS = stringPreferencesKey("gps_fastest_interval_ms")
    val GPS_WORKMANAGER_START_TIME = stringPreferencesKey("gps_workmanager_start_time")
}
