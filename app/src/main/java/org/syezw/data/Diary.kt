package org.syezw.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "diary_list")
@TypeConverters(Converters::class)
data class Diary(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val author: String = "SYEZW",
    val tags: List<String>,
    val timestamp: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val location: String? = null,
    // Store only image file names (e.g. "img_123.jpg"); resolve full path at use time.
    val imageUris: List<String> = emptyList()
)
