package org.syezw

import java.time.LocalDate
import java.time.temporal.ChronoUnit
//import java.text.NumberFormat

object Utils {
    fun daysFromTodayTo(targetYear: Int, targetMonth: Int, targetDay: Int): Long? {
        return try {
            val today = LocalDate.now()
            val target = LocalDate.of(targetYear, targetMonth, targetDay)
            ChronoUnit.DAYS.between(target, today) + 1
        } catch (e: Exception) {
            0
        }
    }
}