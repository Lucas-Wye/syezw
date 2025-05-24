package org.syezw

data class Task(
    val id: Int,
    val name: String,
    var isCompleted: Boolean = false
)