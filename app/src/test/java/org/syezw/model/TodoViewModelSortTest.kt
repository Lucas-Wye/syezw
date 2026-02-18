package org.syezw.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.syezw.data.TodoTask

class TodoViewModelSortTest {

    @Test
    fun sortTodoTasks_ordersIncompleteFirst_thenCompletedByCompletedAtDesc() {
        val tasks = listOf(
            TodoTask(id = 1, name = "done-old", isCompleted = true, createdAt = 100, completedAt = 200),
            TodoTask(id = 2, name = "todo-new", isCompleted = false, createdAt = 500, completedAt = null),
            TodoTask(id = 3, name = "done-new", isCompleted = true, createdAt = 300, completedAt = 900),
            TodoTask(id = 4, name = "todo-old", isCompleted = false, createdAt = 100, completedAt = null)
        )

        val sorted = sortTodoTasks(tasks)

        assertEquals(listOf(2, 4, 3, 1), sorted.map { it.id })
    }

    @Test
    fun sortTodoTasks_putsCompletedWithoutCompletedAtAtEndOfCompletedGroup() {
        val tasks = listOf(
            TodoTask(id = 1, name = "done-with-time", isCompleted = true, createdAt = 100, completedAt = 200),
            TodoTask(id = 2, name = "done-no-time", isCompleted = true, createdAt = 300, completedAt = null)
        )

        val sorted = sortTodoTasks(tasks)

        assertEquals(listOf(1, 2), sorted.map { it.id })
    }
}
