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

    fun extractDateComponents(dateString: String): Triple<Int, Int, Int>? {
        return try {
            val parts = dateString.split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                Triple(year, month, day)
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun isSpecial(target: Long): Boolean {
        if ((target == 99L) || (target == 999L) || (target == 9999L) || (target == 99999L)) {
            return true
        } else if (target % 100L == 0L) {
            return true
        } else if (target % 365L == 0L) {
            return true
        } else {
            return false
        }
    }
//    fun exportJson(context: Context, uri: Uri): Result<Unit> {
//        val json = Json.encodeToString(tagNoteRepo.queryAllNoteShowBeanList().toSet())
//        return runCatching {
//            BufferedOutputStream(context.contentResolver.openOutputStream(uri)).use { out: BufferedOutputStream ->
//                out.write(json.toByteArray())
//            }
//        }
//    }
}

