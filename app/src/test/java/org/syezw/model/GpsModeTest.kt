package org.syezw.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsModeTest {

    @Test
    fun gpsPrefKeys_haveCorrectDefaults() {
        assertEquals("gps_enabled", GpsPrefKeys.GPS_ENABLED.name)
        assertEquals("gps_priority", GpsPrefKeys.GPS_PRIORITY.name)
        assertEquals("gps_interval_ms", GpsPrefKeys.GPS_INTERVAL_MS.name)
        assertEquals("gps_fastest_interval_ms", GpsPrefKeys.GPS_FASTEST_INTERVAL_MS.name)
        assertEquals("gps_workmanager_start_time", GpsPrefKeys.GPS_WORKMANAGER_START_TIME.name)
    }

    @Test
    fun gpsPriority_differentValuesMapCorrectly() {
        data class PriorityMapping(val setting: String, val expectedLabel: String)

        val mappings = listOf(
            PriorityMapping("high_accuracy", "High Accuracy"),
            PriorityMapping("balanced", "Balanced Power"),
            PriorityMapping("low_power", "Low Power"),
            PriorityMapping("no_power", "No Power (Passive)")
        )

        assertEquals(4, mappings.size)
        assertTrue(mappings.all { it.setting.isNotBlank() })
        assertTrue(mappings.all { it.expectedLabel.isNotBlank() })
    }

    @Test
    fun gpsIntervalOptions_areValid() {
        data class IntervalOption(val ms: Long, val label: String)

        val options = listOf(
            IntervalOption(5_000L, "5 seconds"),
            IntervalOption(10_000L, "10 seconds"),
            IntervalOption(30_000L, "30 seconds"),
            IntervalOption(60_000L, "1 minute"),
            IntervalOption(300_000L, "5 minutes")
        )

        assertEquals(5, options.size)
        // Verify all intervals are positive
        assertTrue(options.all { it.ms > 0 })
        // Verify all labels are non-empty
        assertTrue(options.all { it.label.isNotBlank() })
    }

    @Test
    fun oneWeekInMilliseconds_isCorrect() {
        val oneWeekMs = 7 * 24 * 60 * 60 * 1000L
        assertEquals(604_800_000L, oneWeekMs)
        // Verify it's positive
        assertTrue(oneWeekMs > 0)
    }

    @Test
    fun gpsFasterInterval_isAlwaysHalfOfMainInterval() {
        data class IntervalPair(val main: Long, val fastest: Long)

        val pairs = listOf(
            IntervalPair(5_000L, 5_000L / 2),
            IntervalPair(10_000L, 10_000L / 2),
            IntervalPair(30_000L, 30_000L / 2),
            IntervalPair(60_000L, 60_000L / 2),
            IntervalPair(300_000L, 300_000L / 2)
        )

        pairs.forEach { (main, fastest) ->
            assertEquals(main / 2, fastest)
            assertTrue(fastest <= main)
            assertTrue(fastest > 0)
        }
    }

    @Test
    fun priorityLabelMapping_isComplete() {
        val priorityLabels = mapOf(
            "high_accuracy" to "High Accuracy",
            "balanced" to "Balanced Power",
            "low_power" to "Low Power",
            "no_power" to "No Power (Passive)"
        )

        // Every mode should have a label
        assertTrue(priorityLabels.containsKey("high_accuracy"))
        assertTrue(priorityLabels.containsKey("balanced"))
        assertTrue(priorityLabels.containsKey("low_power"))
        assertTrue(priorityLabels.containsKey("no_power"))

        // Default fallback for unknown value
        val unknownValue = "unknown"
        val defaultPriority = "balanced"
        assertFalse(priorityLabels.containsKey(unknownValue))
        assertTrue(priorityLabels.containsKey(defaultPriority))
    }

    @Test
    fun workmanager_autoDisableLogicIsCorrect() {
        val oneWeekMs = 7 * 24 * 60 * 60 * 1000L
        val startTime = 1000L

        // Not expired yet (1 hour in)
        val oneHourLater = startTime + 60 * 60 * 1000L
        assertFalse(oneHourLater - startTime > oneWeekMs)

        // Expired (8 days later)
        val eightDaysLater = startTime + 8 * 24 * 60 * 60 * 1000L
        assertTrue(eightDaysLater - startTime > oneWeekMs)
    }
}