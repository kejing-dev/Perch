package io.embodit.perch.data.local

import io.embodit.perch.data.repository.PerchSnapshot
import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.domain.model.Task
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hand-written JSON codec (org.json ships with Android, so no extra dependency or plugin).
 * Unknown fields are ignored and missing fields fall back to defaults, so older files keep loading.
 */
object PerchJsonCodec {
    private const val VERSION = 1

    fun encode(snapshot: PerchSnapshot): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("tasks", JSONArray().apply { snapshot.tasks.forEach { put(encodeTask(it)) } })
        root.put("sessions", JSONArray().apply { snapshot.sessions.forEach { put(encodeSession(it)) } })
        root.put("settings", encodeSettings(snapshot.settings))
        return root.toString()
    }

    fun decode(text: String): PerchSnapshot {
        if (text.isBlank()) return PerchSnapshot()
        val root = JSONObject(text)
        val tasks = root.optJSONArray("tasks")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::decodeTask) }
        } ?: emptyList()
        val sessions = root.optJSONArray("sessions")?.let { array ->
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::decodeSession) }
        } ?: emptyList()
        val settings = root.optJSONObject("settings")?.let(::decodeSettings) ?: FocusSettings()
        return PerchSnapshot(tasks = tasks, sessions = sessions, settings = settings)
    }

    private fun encodeTask(task: Task): JSONObject =
        JSONObject()
            .put("id", task.id)
            .put("title", task.title)
            .put("createdAt", task.createdAtMillis)
            .put("sortOrder", task.sortOrder)
            .put("completedAt", task.completedAtMillis ?: JSONObject.NULL)

    private fun decodeTask(obj: JSONObject): Task? {
        val id = obj.optString("id", "")
        if (id.isEmpty()) return null
        return Task(
            id = id,
            title = obj.optString("title", ""),
            createdAtMillis = obj.optLong("createdAt", 0L),
            sortOrder = obj.optInt("sortOrder", 0),
            completedAtMillis = if (obj.isNull("completedAt")) null else obj.optLong("completedAt"),
        )
    }

    private fun encodeSession(session: FocusSession): JSONObject =
        JSONObject()
            .put("id", session.id)
            .put("kind", session.kind.name)
            .put("taskId", session.taskId ?: JSONObject.NULL)
            .put("taskTitle", session.taskTitle)
            .put("startedAt", session.startedAtMillis)
            .put("plannedSeconds", session.plannedSeconds)
            .put("elapsedSeconds", session.elapsedSeconds)
            .put("endedAt", session.endedAtMillis ?: JSONObject.NULL)
            .put("outcome", session.outcome.name)

    private fun decodeSession(obj: JSONObject): FocusSession? {
        val id = obj.optString("id", "")
        if (id.isEmpty()) return null
        return FocusSession(
            id = id,
            kind = enumOrDefault(obj.optString("kind"), SessionKind.FOCUS),
            taskId = if (obj.isNull("taskId")) null else obj.optString("taskId"),
            taskTitle = obj.optString("taskTitle", ""),
            startedAtMillis = obj.optLong("startedAt", 0L),
            plannedSeconds = obj.optInt("plannedSeconds", 0),
            elapsedSeconds = obj.optInt("elapsedSeconds", 0),
            endedAtMillis = if (obj.isNull("endedAt")) null else obj.optLong("endedAt"),
            outcome = enumOrDefault(obj.optString("outcome"), SessionOutcome.COMPLETED),
        )
    }

    private fun encodeSettings(settings: FocusSettings): JSONObject =
        JSONObject()
            .put("focusMinutes", settings.focusMinutes)
            .put("breakMinutes", settings.breakMinutes)
            .put("longBreakMinutes", settings.longBreakMinutes)
            .put("blocksBeforeLongBreak", settings.blocksBeforeLongBreak)
            .put("autoStartBreak", settings.autoStartBreak)
            .put("goalMinutes", settings.goalMinutes)
            .put("dayStartHour", settings.dayStartHour)
            .put("dayEndHour", settings.dayEndHour)
            .put("hasSeenCoachmark", settings.hasSeenCoachmark)

    private fun decodeSettings(obj: JSONObject): FocusSettings {
        val defaults = FocusSettings()
        return FocusSettings(
            focusMinutes = obj.optInt("focusMinutes", defaults.focusMinutes),
            breakMinutes = obj.optInt("breakMinutes", defaults.breakMinutes),
            longBreakMinutes = obj.optInt("longBreakMinutes", defaults.longBreakMinutes),
            blocksBeforeLongBreak = obj.optInt("blocksBeforeLongBreak", defaults.blocksBeforeLongBreak),
            autoStartBreak = obj.optBoolean("autoStartBreak", defaults.autoStartBreak),
            goalMinutes = obj.optInt("goalMinutes", defaults.goalMinutes),
            dayStartHour = obj.optInt("dayStartHour", defaults.dayStartHour),
            dayEndHour = obj.optInt("dayEndHour", defaults.dayEndHour),
            hasSeenCoachmark = obj.optBoolean("hasSeenCoachmark", defaults.hasSeenCoachmark),
        ).clamped()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
