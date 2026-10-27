package io.embodit.perch.domain.model

/**
 * One line of work the user wants to protect a focus block for.
 *
 * Tasks are intentionally tiny: a title, an order, and the moment they were finished.
 * Everything else (how much focus time went into a task) is derived from [FocusSession]s.
 */
data class Task(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val sortOrder: Int,
    val completedAtMillis: Long? = null,
) {
    val isDone: Boolean
        get() = completedAtMillis != null
}
