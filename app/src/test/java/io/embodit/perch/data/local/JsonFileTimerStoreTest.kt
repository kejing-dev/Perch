package io.embodit.perch.data.local

import io.embodit.perch.domain.model.TimerPhase
import io.embodit.perch.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonFileTimerStoreTest {

    @Test
    fun `running state round-trips without the countdown`() {
        val state = TimerState(
            phase = TimerPhase.FOCUS, sessionId = "s", taskId = "t", plannedSeconds = 1500,
            endsAtMillis = 1_700_000_000_000L, remainingSeconds = 1234, completedBlocksInCycle = 2,
        )
        val decoded = JsonFileTimerStore.decode(JsonFileTimerStore.encode(JsonFileTimerStore.projection(state)))
        assertEquals(state.copy(remainingSeconds = 0), decoded)
    }

    @Test
    fun `paused and break-due states keep their timestamps`() {
        val paused = TimerState(
            phase = TimerPhase.PAUSED, sessionId = "s", taskId = "t", plannedSeconds = 1500,
            remainingSeconds = 900, pausedAtMillis = 1_700_000_100_000L,
        )
        assertEquals(paused, JsonFileTimerStore.decode(JsonFileTimerStore.encode(JsonFileTimerStore.projection(paused))))
        val due = TimerState(phase = TimerPhase.BREAK_DUE, sessionId = "s", completedBlocksInCycle = 3, breakDueAtMillis = 1_700_000_200_000L)
        assertEquals(due, JsonFileTimerStore.decode(JsonFileTimerStore.encode(due)))
    }

    @Test
    fun `corrupt or inconsistent files are ignored`() {
        assertNull(JsonFileTimerStore.decode(""))
        assertNull(JsonFileTimerStore.decode("{\"phase\":\"FOCUS\"}"))
        assertNull(JsonFileTimerStore.decode("{\"phase\":\"NOPE\"}"))
    }
}
