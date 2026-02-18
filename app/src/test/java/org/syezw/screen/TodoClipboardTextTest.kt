package org.syezw.screen

import org.junit.Assert.assertTrue
import org.junit.Test
import org.syezw.data.TodoTask

class TodoClipboardTextTest {

    @Test
    fun buildTodoClipboardText_includesCoreFields_forIncompleteTask() {
        val task = TodoTask(
            id = 1,
            name = "买牛奶",
            isCompleted = false,
            createdAt = 1_700_000_000_000,
            completedAt = null
        )

        val text = buildTodoClipboardText(task)

        assertTrue(text.contains("买牛奶"))
    }

    @Test
    fun buildTodoClipboardText_marksCompletedStatus_forCompletedTask() {
        val task = TodoTask(
            id = 2,
            name = "写日报",
            isCompleted = true,
            createdAt = 1_700_000_000_000,
            completedAt = 1_700_000_100_000
        )

        val text = buildTodoClipboardText(task)

        assertTrue(text.contains("写日报"))
    }
}
