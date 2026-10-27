package io.embodit.perch.domain.usecase

import io.embodit.perch.domain.model.TimerPhase
import io.embodit.perch.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerUseCaseTest {

    private val timer = FocusTimerUseCase()
    private val t0 = 1_700_000_000_000L

    @Test
    fun `startFocus sets a wall-clock end and full remaining time`() {
        val state = timer.startFocus(TimerState(), t0, "s1", "task-1", 25 * 60)
        assertEquals(TimerPhase.FOCUS, state.phase)
        assertEquals(t0 + 25 * 60 * 1000L, state.endsAtMillis)
        assertEquals(25 * 60, state.remainingSeconds)
        assertEquals(0f, state.progress, 0.0001f)
    }

    @Test
    fun `tick derives remaining from the clock and rounds up`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 60)
        val afterHalf = timer.tick(started, t0 + 30_000L)
        assertEquals(30, afterHalf.remainingSeconds)
        val almostDone = timer.tick(started, t0 + 59_400L)
        assertEquals(1, almostDone.remainingSeconds)
        assertEquals(TimerPhase.FOCUS, almostDone.phase)
    }

    @Test
    fun `focus block reaching zero becomes BREAK_DUE and counts a block`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 60)
        val done = timer.tick(started, t0 + 60_000L)
        assertEquals(TimerPhase.BREAK_DUE, done.phase)
        assertEquals(0, done.remainingSeconds)
        assertNull(done.endsAtMillis)
        assertEquals(1, done.completedBlocksInCycle)
        assertEquals(1f, done.progress, 0.0001f)
    }

    @Test
    fun `pause freezes remaining and resume continues from it`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 100)
        val paused = timer.pause(started, t0 + 40_000L)
        assertEquals(TimerPhase.PAUSED, paused.phase)
        assertEquals(60, paused.remainingSeconds)
        // Time passes while paused; remaining must not change.
        assertEquals(60, timer.tick(paused, t0 + 90_000L).remainingSeconds)
        val resumed = timer.resume(paused, t0 + 100_000L)
        assertEquals(TimerPhase.FOCUS, resumed.phase)
        assertEquals(t0 + 160_000L, resumed.endsAtMillis)
        assertEquals(30, timer.tick(resumed, t0 + 130_000L).remainingSeconds)
    }

    @Test
    fun `extend adds time to a running block and grows the plan`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 60)
        val extended = timer.extend(started, t0 + 30_000L, 300)
        assertEquals(360, extended.plannedSeconds)
        assertEquals(330, extended.remainingSeconds)
        assertEquals(t0 + 30_000L + 330_000L, extended.endsAtMillis)
    }

    @Test
    fun `stop returns to idle but keeps the cycle count`() {
        var state = timer.startFocus(TimerState(), t0, "s1", "task-1", 60)
        state = timer.tick(state, t0 + 60_000L)
        state = timer.startBreak(state, t0 + 61_000L, "b1", 300)
        assertEquals(TimerPhase.BREAK, state.phase)
        val stopped = timer.stop(state, t0 + 100_000L)
        assertEquals(TimerPhase.IDLE, stopped.phase)
        assertNull(stopped.sessionId)
        assertEquals(1, stopped.completedBlocksInCycle)
        assertFalse(stopped.isRunning)
    }

    @Test
    fun `break reaching zero returns to idle and long break is due every N blocks`() {
        var state = TimerState()
        repeat(4) { index ->
            state = timer.startFocus(state, t0, "s$index", null, 10)
            state = timer.tick(state, t0 + 10_000L)
            assertEquals(TimerPhase.BREAK_DUE, state.phase)
            val long = timer.isLongBreakDue(state, blocksBeforeLongBreak = 4)
            assertEquals(index == 3, long)
            state = timer.startBreak(state, t0 + 11_000L, "b$index", 5)
            state = timer.tick(state, t0 + 16_000L)
            assertEquals(TimerPhase.IDLE, state.phase)
        }
        assertEquals(4, state.completedBlocksInCycle)
    }

    @Test
    fun `skipBreak from BREAK_DUE goes idle`() {
        var state = timer.startFocus(TimerState(), t0, "s1", null, 10)
        state = timer.tick(state, t0 + 10_000L)
        val skipped = timer.skipBreak(state)
        assertEquals(TimerPhase.IDLE, skipped.phase)
        assertTrue(skipped.completedBlocksInCycle == 1)
    }

    @Test
    fun `pause and resume are no-ops outside their phases`() {
        val idle = TimerState()
        assertEquals(idle, timer.pause(idle, t0))
        assertEquals(idle, timer.resume(idle, t0))
        assertEquals(idle, timer.extend(idle, t0, 60))
    }

    @Test
    fun `pause records when it began and resume clears it`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 100)
        val paused = timer.pause(started, t0 + 40_000L)
        assertEquals(t0 + 40_000L, paused.pausedAtMillis)
        assertEquals(90, timer.pausedForSeconds(paused, t0 + 130_000L))
        assertNull(timer.resume(paused, t0 + 130_000L).pausedAtMillis)
        assertEquals(0, timer.pausedForSeconds(started, t0 + 50_000L))
    }

    @Test
    fun `pausing a block that already reached zero lets it end`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 60)
        val state = timer.pause(started, t0 + 61_000L)
        assertEquals(TimerPhase.BREAK_DUE, state.phase)
        assertEquals(1, state.completedBlocksInCycle)
    }

    @Test
    fun `break auto-start counts down from the moment the block ended`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 60)
        val due = timer.tick(started, t0 + 60_000L)
        assertEquals(t0 + 60_000L, due.breakDueAtMillis)
        assertEquals(20, timer.autoStartSecondsLeft(due, t0 + 60_000L, 20))
        assertEquals(5, timer.autoStartSecondsLeft(due, t0 + 75_000L, 20))
        assertFalse(timer.shouldAutoStartBreak(due, t0 + 79_000L, 20))
        assertTrue(timer.shouldAutoStartBreak(due, t0 + 80_000L, 20))
    }

    @Test
    fun `a break countdown nobody watched to its end does not fire on return`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 60)
        val due = timer.tick(started, t0 + 60_000L)
        // Window closed during the 20 s countdown, reopened two hours later.
        assertFalse(timer.shouldAutoStartBreak(due, t0 + 60_000L + 7_200_000L, 20))
        assertEquals(0, timer.autoStartSecondsLeft(due, t0 + 60_000L + 7_200_000L, 20))
        // A tick that is a little late still starts it.
        assertTrue(timer.shouldAutoStartBreak(due, t0 + 83_000L, 20))
    }

    @Test
    fun `a block that ended while the app was away never auto-starts its break`() {
        val started = timer.startFocus(TimerState(), t0, "s1", null, 60)
        val due = timer.tick(started, t0 + 600_000L)
        assertEquals(TimerPhase.BREAK_DUE, due.phase)
        assertNull(due.breakDueAtMillis)
        assertEquals(0, timer.autoStartSecondsLeft(due, t0 + 600_000L, 20))
        assertFalse(timer.shouldAutoStartBreak(due, t0 + 700_000L, 20))
    }
}
