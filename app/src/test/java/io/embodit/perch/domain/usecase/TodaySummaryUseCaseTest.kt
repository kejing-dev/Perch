package io.embodit.perch.domain.usecase

import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.domain.model.Task
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class TodaySummaryUseCaseTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val useCase = TodaySummaryUseCase(utc)

    // 2026-09-25T10:00:00Z
    private val now = 1_790_244_000_000L
    private val dayStart = now - 10L * 60L * 60L * 1000L

    private fun session(id: String, startedAt: Long, elapsed: Int, kind: SessionKind = SessionKind.FOCUS, outcome: SessionOutcome = SessionOutcome.COMPLETED) =
        FocusSession(id = id, kind = kind, taskId = null, taskTitle = "t", startedAtMillis = startedAt, plannedSeconds = elapsed, elapsedSeconds = elapsed, endedAtMillis = startedAt + elapsed * 1000L, outcome = outcome)

    @Test
    fun `startOfDay is local midnight`() {
        assertEquals(dayStart, useCase.startOfDay(now))
    }

    @Test
    fun `only sessions started today count and breaks are excluded from focus minutes`() {
        val sessions = listOf(
            session("yesterday", dayStart - 1L, 1500),
            session("morning", dayStart + 3_600_000L, 1500),
            session("break", dayStart + 5_000_000L, 300, kind = SessionKind.BREAK),
            session("stopped", dayStart + 6_000_000L, 600, outcome = SessionOutcome.STOPPED_EARLY),
        )
        val summary = useCase.summarize(sessions, emptyList(), now)
        assertEquals(2100, summary.focusSeconds)
        assertEquals(1, summary.completedBlocks)
        assertEquals(listOf("morning", "break", "stopped"), summary.sessionsToday.map { it.id })
    }

    @Test
    fun `day boundaries use calendar days so DST days keep their real length`() {
        val la = TodaySummaryUseCase(TimeZone.getTimeZone("America/Los_Angeles"))
        // 2026-11-01 is the fall-back day in Los Angeles: 25 hours long.
        val start = la.startOfDay(1_793_520_000_000L)
        assertEquals(25L * 60L * 60L * 1000L, la.nextDayStart(start) - start)
    }

    @Test
    fun `completed tasks count only those finished today`() {
        val tasks = listOf(
            Task("a", "a", 0L, 0, completedAtMillis = dayStart + 10L),
            Task("b", "b", 0L, 1, completedAtMillis = dayStart - 10L),
            Task("c", "c", 0L, 2, completedAtMillis = null),
        )
        assertEquals(1, useCase.summarize(emptyList(), tasks, now).completedTasks)
    }
}
