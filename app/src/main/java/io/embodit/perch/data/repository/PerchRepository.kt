package io.embodit.perch.data.repository

import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.domain.model.Task
import kotlinx.coroutines.flow.StateFlow

/** Everything Perch persists, as one immutable snapshot. */
data class PerchSnapshot(
    val tasks: List<Task> = emptyList(),
    val sessions: List<FocusSession> = emptyList(),
    val settings: FocusSettings = FocusSettings(),
)

/**
 * Single source of truth for tasks, the session log and settings. Implementations must be safe to
 * call from any coroutine; observers see every change through [snapshot].
 */
interface PerchRepository {
    val snapshot: StateFlow<PerchSnapshot>

    /** False until persisted data has been read; consumers that reconcile state must wait for it. */
    val isLoaded: StateFlow<Boolean>

    /** True when the persisted file existed but could not be read (it is kept aside, not overwritten). */
    val loadError: StateFlow<Boolean>

    suspend fun addTask(task: Task)
    suspend fun updateTask(task: Task)
    suspend fun replaceTasks(tasks: List<Task>)
    suspend fun deleteTask(taskId: String)

    suspend fun upsertSession(session: FocusSession)

    suspend fun updateSettings(settings: FocusSettings)
}
