package org.syezw.data

import java.time.LocalDate
import org.syezw.model.PeriodRecord

/**
 * Data classes for importing from JSON files.
 * All fields are nullable to handle missing fields in legacy JSON exports.
 */

data class DiaryImport(
    val id: Int? = null,
    val uuid: String? = null,
    val content: String? = null,
    val author: String? = null,
    val tags: List<String>? = null,
    val timestamp: Long? = null,
    val updatedAt: Long? = null,
    val location: String? = null,
    val imageUris: List<String>? = null
) {
    fun toDiary(): Diary {
        return Diary(
            id = 0, // Always generate new ID
            uuid = uuid ?: java.util.UUID.randomUUID().toString(),
            content = content ?: "",
            author = author ?: "SYEZW",
            tags = tags ?: emptyList(),
            timestamp = timestamp ?: System.currentTimeMillis(),
            updatedAt = updatedAt ?: timestamp ?: System.currentTimeMillis(),
            location = location,
            imageUris = imageUris ?: emptyList()
        )
    }
}

data class TodoTaskImport(
    val id: Int? = null,
    val uuid: String? = null,
    val name: String? = null,
    val author: String? = null,
    val isCompleted: Boolean? = null,
    val createdAt: Long? = null,
    val completedAt: Long? = null,
    val updatedAt: Long? = null
) {
    fun toTodoTask(): TodoTask {
        return TodoTask(
            id = 0, // Always generate new ID
            uuid = uuid ?: java.util.UUID.randomUUID().toString(),
            name = name ?: "",
            author = author ?: "SYEZW",
            isCompleted = isCompleted ?: false,
            createdAt = createdAt ?: System.currentTimeMillis(),
            completedAt = completedAt,
            updatedAt = updatedAt ?: createdAt ?: System.currentTimeMillis()
        )
    }
}

data class PeriodRecordImport(
    val startDate: String? = null,
    val endDate: String? = null,
    val notes: String? = null,
    val updatedAt: Long? = null
) {
    fun toPeriodRecord(): PeriodRecord? {
        val start = startDate?.let { LocalDate.parse(it) } ?: return null
        val end = endDate?.let { LocalDate.parse(it) } ?: return null
        return PeriodRecord(
            startDate = start,
            endDate = end,
            notes = notes,
            updatedAt = updatedAt ?: System.currentTimeMillis()
        )
    }
}
