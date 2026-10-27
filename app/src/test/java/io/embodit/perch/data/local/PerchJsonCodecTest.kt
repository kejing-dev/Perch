package io.embodit.perch.data.local

import io.embodit.perch.data.repository.PerchSnapshot
import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

class PerchJsonCodecTest {

    @Test
    fun `snapshot round-trips`() {
        val snapshot = PerchSnapshot(
            tasks = listOf(
                Task("a", "Write \"the\" brief", 10L, 0),
                Task("b", "Done one", 20L, 1, completedAtMillis = 99L),
            ),
            sessions = listOf(
                FocusSession("s1", SessionKind.FOCUS, "a", "Write the brief", 100L, 1500, 1500, 1_600_100L, SessionOutcome.COMPLETED),
                FocusSession("s2", SessionKind.BREAK, null, "", 200L, 300),
            ),
            settings = FocusSettings(focusMinutes = 45, breakMinutes = 7, goalMinutes = 240, dayStartHour = 8, dayEndHour = 20, hasSeenCoachmark = true),
        )
        assertEquals(snapshot, PerchJsonCodec.decode(PerchJsonCodec.encode(snapshot)))
    }

    @Test
    fun `blank text and missing fields fall back to defaults`() {
        assertEquals(PerchSnapshot(), PerchJsonCodec.decode(""))
        val decoded = PerchJsonCodec.decode("{\"tasks\":[{\"id\":\"x\"},{\"title\":\"no id\"}],\"settings\":{\"focusMinutes\":9999}}")
        assertEquals(listOf(Task("x", "", 0L, 0)), decoded.tasks)
        assertEquals(FocusSettings.MAX_FOCUS_MINUTES, decoded.settings.focusMinutes)
    }
}
