package org.syezw.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_list")
data class TodoTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val author: String = "SYEZW",
    var isCompleted: Boolean = false,
    val createdAt: Long,
    val completedAt: Long?
)