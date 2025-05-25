package org.syezw.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diary_list")
data class Diary(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val createdAt: Long,
    val author: String,
    val content: String,
)