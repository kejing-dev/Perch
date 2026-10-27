package io.embodit.perch.data.repository

import io.embodit.perch.data.local.PerchJsonCodec
import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.domain.model.Task
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Persists the snapshot as one JSON file (`perch.json`) in the app's private files directory.
 * Every mutation updates memory and then writes the whole file (temp file + fsync + rename) while
 * holding one mutex, so writes can neither interleave nor reorder. The file is small (tasks plus a
 * day-scale session log), so whole-file writes are the simplest correct strategy.
 *
 * A file that exists but cannot be decoded is moved aside as `perch.json.corrupt` and reported
 * through [loadError]; it is never silently overwritten.
 */
class JsonFilePerchRepository(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PerchRepository {

    private val state = MutableStateFlow(PerchSnapshot())
    override val snapshot: StateFlow<PerchSnapshot> = state
    private val loaded = MutableStateFlow(false)
    override val isLoaded: StateFlow<Boolean> = loaded
    private val failed = MutableStateFlow(false)
    override val loadError: StateFlow<Boolean> = failed
    private val mutex = Mutex()

    /** Loads the file once. A missing file yields defaults; an unreadable one is kept aside. */
    suspend fun load() {
        mutex.withLock {
            if (loaded.value) return
            val result = withContext(io) {
                if (!file.exists()) return@withContext PerchSnapshot()
                runCatching { PerchJsonCodec.decode(file.readText()) }.getOrElse {
                    runCatching { file.renameTo(File(file.parentFile, file.name + ".corrupt")) }
                    failed.value = true
                    PerchSnapshot()
                }
            }
            state.value = result
            loaded.value = true
        }
    }

    override suspend fun addTask(task: Task) = mutate { it.copy(tasks = it.tasks + task) }

    override suspend fun updateTask(task: Task) =
        mutate { snap -> snap.copy(tasks = snap.tasks.map { if (it.id == task.id) task else it }) }

    override suspend fun replaceTasks(tasks: List<Task>) = mutate { it.copy(tasks = tasks) }

    override suspend fun deleteTask(taskId: String) =
        mutate { snap -> snap.copy(tasks = snap.tasks.filterNot { it.id == taskId }) }

    override suspend fun upsertSession(session: FocusSession) =
        mutate { snap ->
            val exists = snap.sessions.any { it.id == session.id }
            val sessions =
                if (exists) snap.sessions.map { if (it.id == session.id) session else it }
                else snap.sessions + session
            snap.copy(sessions = sessions.takeLast(MAX_SESSIONS))
        }

    override suspend fun updateSettings(settings: FocusSettings) =
        mutate { it.copy(settings = settings.clamped()) }

    private suspend fun mutate(transform: (PerchSnapshot) -> PerchSnapshot) {
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            withContext(io) { persist(updated) }
        }
    }

    private fun persist(snapshot: PerchSnapshot) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use { out ->
                out.write(PerchJsonCodec.encode(snapshot).toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.writeText(PerchJsonCodec.encode(snapshot))
                tmp.delete()
            }
        }
    }

    private companion object {
        /** Keep roughly a quarter of daily-use history; the UI only reads a week. */
        const val MAX_SESSIONS = 2000
    }
}
