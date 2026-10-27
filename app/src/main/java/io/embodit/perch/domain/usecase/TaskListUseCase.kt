package io.embodit.perch.domain.usecase

import io.embodit.perch.domain.model.Task

/**
 * Ordering rules for the task list: open tasks first in the user's order, finished tasks after,
 * most recently finished first. The "next" task is simply the first open one.
 */
class TaskListUseCase {

    fun ordered(tasks: List<Task>): List<Task> {
        val (done, open) = tasks.partition { it.isDone }
        return open.sortedBy { it.sortOrder } + done.sortedByDescending { it.completedAtMillis ?: 0L }
    }

    fun nextTask(tasks: List<Task>): Task? = ordered(tasks).firstOrNull { !it.isDone }

    fun openTasks(tasks: List<Task>): List<Task> = ordered(tasks).filter { !it.isDone }

    /**
     * Moves [taskId] to the top of the open list by re-numbering sort orders. With [keepPerched] the
     * current first task stays where it is and [taskId] becomes next in line behind it.
     */
    fun promoteToNext(tasks: List<Task>, taskId: String, keepPerched: Boolean = false): List<Task> {
        val open = openTasks(tasks)
        val target = open.firstOrNull { it.id == taskId } ?: return tasks
        val perched = open.first()
        val reordered =
            if (keepPerched && perched.id != taskId) {
                listOf(perched, target) + open.filter { it.id != taskId && it.id != perched.id }
            } else {
                listOf(target) + open.filter { it.id != taskId }
            }
        return applyOrder(tasks, reordered.map { it.id })
    }

    /** Moves an open task one position up or down. */
    fun move(tasks: List<Task>, taskId: String, delta: Int): List<Task> {
        val open = openTasks(tasks).toMutableList()
        val index = open.indexOfFirst { it.id == taskId }
        if (index < 0) return tasks
        val target = (index + delta).coerceIn(0, open.lastIndex)
        if (target == index) return tasks
        val item = open.removeAt(index)
        open.add(target, item)
        return applyOrder(tasks, open.map { it.id })
    }

    /** Normalizes a title typed by the user; returns null when nothing usable was typed. */
    fun normalizeTitle(raw: String): String? {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return null
        return trimmed.take(MAX_TITLE_LENGTH)
    }

    private fun applyOrder(tasks: List<Task>, orderedOpenIds: List<String>): List<Task> {
        val orderById = orderedOpenIds.withIndex().associate { (index, id) -> id to index }
        return tasks.map { task ->
            val order = orderById[task.id]
            if (order != null) task.copy(sortOrder = order) else task
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 120
    }
}
