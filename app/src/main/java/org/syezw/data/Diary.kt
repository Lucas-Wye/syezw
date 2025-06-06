package org.syezw.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_list")
data class Diary(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val content: String,
    val author: String = "SYEZW",
    val tags: List<String>, // List of tags
    val timestamp: Long,    // Store time as Long (e.g., milliseconds since epoch)
    val location: String? = null
)