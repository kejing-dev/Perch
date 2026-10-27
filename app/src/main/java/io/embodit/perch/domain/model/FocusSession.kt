package io.embodit.perch.domain.model

enum class SessionKind { FOCUS, BREAK }

enum class SessionOutcome {
    /** The block is still counting down. */
    RUNNING,
    /** The planned time elapsed. */
    COMPLETED,
    /** The user ended the block before the planned time elapsed. */
    STOPPED_EARLY,
}

/**
 * One timed block. A focus block is logged for the task that was perched when it started, or the
 * one the user switched it to mid-block; break blocks have no task. The log for "today" is the
 * list of these.
 */
data class FocusSession(
    val id: String,
    val kind: SessionKind,
    val taskId: String?,
    val taskTitle: String,
    val startedAtMillis: Long,
    val plannedSeconds: Int,
    val elapsedSeconds: Int = 0,
    val endedAtMillis: Long? = null,
    val outcome: SessionOutcome = SessionOutcome.RUNNING,
)
