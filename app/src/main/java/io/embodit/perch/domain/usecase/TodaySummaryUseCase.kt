package io.embodit.perch.domain.usecase

import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.domain.model.Task
import java.util.Calendar
import java.util.TimeZone

data class TodaySummary(
    val focusSeconds: Int,
    val completedBlocks: Int,
    val completedTasks: Int,
    val sessionsToday: List<FocusSession>,
)

/**
 * Rolls the session log and task list up into the numbers the "done today" view shows.
 * Day boundaries follow [timeZone] (calendar arithmetic, so DST days keep their real length);
 * a block counts toward the day it started.
 */
class TodaySummaryUseCase(private val timeZone: TimeZone = TimeZone.getDefault()) {

    fun summarize(sessions: List<FocusSession>, tasks: List<Task>, nowMillis: Long): TodaySummary {
        val dayStart = startOfDay(nowMillis)
        val dayEnd = nextDayStart(dayStart)
        val today = sessions
            .filter { it.startedAtMillis in dayStart until dayEnd }
            .sortedBy { it.startedAtMillis }
        val focusToday = today.filter { it.kind == SessionKind.FOCUS }
        return TodaySummary(
            focusSeconds = focusToday.sumOf { it.elapsedSeconds },
            completedBlocks = focusToday.count { it.outcome == SessionOutcome.COMPLETED },
            completedTasks = tasks.count { task ->
                val done = task.completedAtMillis ?: return@count false
                done in dayStart until dayEnd
            },
            sessionsToday = today,
        )
    }

    fun startOfDay(nowMillis: Long): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Midnight after [dayStartMillis], honoring DST transitions. */
    fun nextDayStart(dayStartMillis: Long): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = dayStartMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Midnight [days] days before [dayStartMillis]. */
    fun daysBefore(dayStartMillis: Long, days: Int): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = dayStartMillis
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return cal.timeInMillis
    }
}
