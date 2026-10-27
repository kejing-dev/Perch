package io.embodit.perch.domain.usecase

import io.embodit.perch.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskListUseCaseTest {

    private val useCase = TaskListUseCase()

    private fun task(id: String, order: Int, doneAt: Long? = null) =
        Task(id = id, title = "Task $id", createdAtMillis = 0L, sortOrder = order, completedAtMillis = doneAt)

    @Test
    fun `ordered puts open tasks first by sortOrder and done tasks last by completion time`() {
        val tasks = listOf(task("c", 2), task("a", 0), task("done-old", 5, 100L), task("b", 1), task("done-new", 9, 200L))
        val ids = useCase.ordered(tasks).map { it.id }
        assertEquals(listOf("a", "b", "c", "done-new", "done-old"), ids)
    }

    @Test
    fun `nextTask is the first open task and null when everything is done`() {
        assertEquals("a", useCase.nextTask(listOf(task("b", 1), task("a", 0)))?.id)
        assertNull(useCase.nextTask(listOf(task("x", 0, 1L))))
        assertNull(useCase.nextTask(emptyList()))
    }

    @Test
    fun `promoteToNext moves the task to the top and renumbers the rest`() {
        val tasks = listOf(task("a", 0), task("b", 1), task("c", 2))
        val promoted = useCase.promoteToNext(tasks, "c")
        assertEquals(listOf("c", "a", "b"), useCase.openTasks(promoted).map { it.id })
        assertEquals(listOf(0, 1, 2), useCase.openTasks(promoted).map { it.sortOrder })
    }

    @Test
    fun `move clamps at the edges and ignores done or unknown tasks`() {
        val tasks = listOf(task("a", 0), task("b", 1), task("done", 2, 5L))
        assertEquals(listOf("b", "a"), useCase.openTasks(useCase.move(tasks, "b", -1)).map { it.id })
        assertEquals(tasks, useCase.move(tasks, "a", -1))
        assertEquals(tasks, useCase.move(tasks, "done", -1))
        assertEquals(tasks, useCase.move(tasks, "nope", 1))
    }

    @Test
    fun `normalizeTitle trims collapses whitespace and rejects empty`() {
        assertEquals("Write   the brief".replace(Regex("\\s+"), " "), useCase.normalizeTitle("  Write   the brief \n"))
        assertNull(useCase.normalizeTitle("   "))
        assertEquals(TaskListUseCase.MAX_TITLE_LENGTH, useCase.normalizeTitle("x".repeat(500))!!.length)
    }

    @Test
    fun `promote with keepPerched lines the task up behind the first one`() {
        val tasks = listOf(Task("a", "A", 0L, 0), Task("b", "B", 0L, 1), Task("c", "C", 0L, 2))
        val useCase = TaskListUseCase()
        assertEquals(listOf("a", "c", "b"), useCase.openTasks(useCase.promoteToNext(tasks, "c", keepPerched = true)).map { it.id })
        assertEquals(listOf("a", "b", "c"), useCase.openTasks(useCase.promoteToNext(tasks, "a", keepPerched = true)).map { it.id })
        assertEquals(listOf("c", "a", "b"), useCase.openTasks(useCase.promoteToNext(tasks, "c")).map { it.id })
    }
}
