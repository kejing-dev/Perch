package io.embodit.perch.data.repository

import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.domain.model.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield

/**
 * In-memory repository for unit tests and previews. Every mutation yields once so tests see the
 * same suspension points a real file-backed repository has.
 */
class FakePerchRepository(
    initial: PerchSnapshot = PerchSnapshot(),
    loaded: Boolean = true,
    loadError: Boolean = false,
) : PerchRepository {

    private val state = MutableStateFlow(initial)
    override val snapshot: StateFlow<PerchSnapshot> = state
    private val loadedFlag = MutableStateFlow(loaded)
    override val isLoaded: StateFlow<Boolean> = loadedFlag
    override val loadError: StateFlow<Boolean> = MutableStateFlow(loadError)

    /** Simulates the end of an asynchronous load. */
    fun markLoaded() {
        loadedFlag.value = true
    }

    override suspend fun addTask(task: Task) {
        state.update { it.copy(tasks = it.tasks + task) }
        yield()
    }

    override suspend fun updateTask(task: Task) {
        state.update { snap -> snap.copy(tasks = snap.tasks.map { if (it.id == task.id) task else it }) }
        yield()
    }

    override suspend fun replaceTasks(tasks: List<Task>) {
        state.update { it.copy(tasks = tasks) }
        yield()
    }

    override suspend fun deleteTask(taskId: String) {
        state.update { snap -> snap.copy(tasks = snap.tasks.filterNot { it.id == taskId }) }
        yield()
    }

    override suspend fun upsertSession(session: FocusSession) {
        state.update { snap ->
            val exists = snap.sessions.any { it.id == session.id }
            val sessions = if (exists) snap.sessions.map { if (it.id == session.id) session else it } else snap.sessions + session
            snap.copy(sessions = sessions)
        }
        yield()
    }

    override suspend fun updateSettings(settings: FocusSettings) {
        state.update { it.copy(settings = settings.clamped()) }
        yield()
    }
}
