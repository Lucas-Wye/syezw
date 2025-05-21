package org.syezw.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "diary_list")
@TypeConverters(Converters::class)
data class Diary(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val content: String,
    val author: String = "SYEZW",
    val tags: List<String>,
    val timestamp: Long,
    val location: String? = null,
    val imageUris: List<String> = emptyList()
)