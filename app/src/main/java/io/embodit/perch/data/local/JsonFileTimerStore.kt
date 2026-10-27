package io.embodit.perch.data.local

import io.embodit.perch.domain.model.TimerPhase
import io.embodit.perch.domain.model.TimerState
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Persists the live timer (`timer.json`) so a process death mid-block resumes where it was.
 * Because remaining time is derived from the wall-clock end, only phase changes need to be
 * written, never the per-second countdown; [projection] strips the volatile field.
 */
class JsonFileTimerStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(): TimerState? =
        withContext(io) {
            runCatching { if (file.exists()) decode(file.readText()) else null }.getOrNull()
        }

    suspend fun save(state: TimerState) {
        withContext(io) {
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                FileOutputStream(tmp).use { out ->
                    out.write(encode(state).toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                if (!tmp.renameTo(file)) {
                    file.writeText(encode(state))
                    tmp.delete()
                }
            }
        }
    }

    companion object {
        /** The part of the timer worth writing: everything except the per-second countdown of a running block. */
        fun projection(state: TimerState): TimerState =
            if (state.isRunning) state.copy(remainingSeconds = 0) else state

        fun encode(state: TimerState): String =
            JSONObject()
                .put("phase", state.phase.name)
                .put("sessionId", state.sessionId ?: JSONObject.NULL)
                .put("taskId", state.taskId ?: JSONObject.NULL)
                .put("plannedSeconds", state.plannedSeconds)
                .put("endsAtMillis", state.endsAtMillis ?: JSONObject.NULL)
                .put("remainingSeconds", state.remainingSeconds)
                .put("completedBlocksInCycle", state.completedBlocksInCycle)
                .put("pausedAtMillis", state.pausedAtMillis ?: JSONObject.NULL)
                .put("breakDueAtMillis", state.breakDueAtMillis ?: JSONObject.NULL)
                .toString()

        fun decode(text: String): TimerState? {
            if (text.isBlank()) return null
            val obj = JSONObject(text)
            val phase = TimerPhase.entries.firstOrNull { it.name == obj.optString("phase") } ?: return null
            val planned = obj.optInt("plannedSeconds", 0)
            val endsAt = if (obj.isNull("endsAtMillis")) null else obj.optLong("endsAtMillis")
            // A running block needs its end; a paused one needs its remaining time. Anything else is IDLE.
            val valid = when (phase) {
                TimerPhase.FOCUS, TimerPhase.BREAK -> endsAt != null && planned > 0
                TimerPhase.PAUSED -> planned > 0
                TimerPhase.BREAK_DUE, TimerPhase.IDLE -> true
            }
            if (!valid) return null
            return TimerState(
                phase = phase,
                sessionId = if (obj.isNull("sessionId")) null else obj.optString("sessionId"),
                taskId = if (obj.isNull("taskId")) null else obj.optString("taskId"),
                plannedSeconds = planned,
                endsAtMillis = endsAt,
                remainingSeconds = obj.optInt("remainingSeconds", 0).coerceIn(0, maxOf(planned, 0)),
                completedBlocksInCycle = obj.optInt("completedBlocksInCycle", 0).coerceAtLeast(0),
                pausedAtMillis = if (obj.isNull("pausedAtMillis")) null else obj.optLong("pausedAtMillis"),
                breakDueAtMillis = if (obj.isNull("breakDueAtMillis")) null else obj.optLong("breakDueAtMillis"),
            )
        }
    }
}
