package io.embodit.perch.domain.usecase

import io.embodit.perch.domain.model.TimerPhase
import io.embodit.perch.domain.model.TimerState

/**
 * Pure reducer for the focus timer. Every function takes the current state plus the wall clock
 * and returns the next state; no coroutines, no Android, so the whole block lifecycle is unit
 * testable and the ViewModel only has to call [tick] once a second.
 */
class FocusTimerUseCase {

    fun startFocus(
        state: TimerState,
        nowMillis: Long,
        sessionId: String,
        taskId: String?,
        plannedSeconds: Int,
    ): TimerState {
        require(plannedSeconds > 0) { "plannedSeconds must be positive" }
        return state.copy(
            phase = TimerPhase.FOCUS,
            sessionId = sessionId,
            taskId = taskId,
            plannedSeconds = plannedSeconds,
            endsAtMillis = nowMillis + plannedSeconds * 1000L,
            remainingSeconds = plannedSeconds,
            pausedAtMillis = null,
            breakDueAtMillis = null,
        )
    }

    fun startBreak(state: TimerState, nowMillis: Long, sessionId: String, plannedSeconds: Int): TimerState {
        require(plannedSeconds > 0) { "plannedSeconds must be positive" }
        return state.copy(
            phase = TimerPhase.BREAK,
            sessionId = sessionId,
            taskId = null,
            plannedSeconds = plannedSeconds,
            endsAtMillis = nowMillis + plannedSeconds * 1000L,
            remainingSeconds = plannedSeconds,
            pausedAtMillis = null,
            breakDueAtMillis = null,
        )
    }

    /** Pausing a block that has already reached zero simply lets it end. */
    fun pause(state: TimerState, nowMillis: Long): TimerState {
        if (state.phase != TimerPhase.FOCUS) return state
        val remaining = remainingAt(state, nowMillis)
        if (remaining <= 0) return tick(state, nowMillis)
        return state.copy(phase = TimerPhase.PAUSED, endsAtMillis = null, remainingSeconds = remaining, pausedAtMillis = nowMillis)
    }

    fun resume(state: TimerState, nowMillis: Long): TimerState {
        if (state.phase != TimerPhase.PAUSED) return state
        return state.copy(
            phase = TimerPhase.FOCUS,
            endsAtMillis = nowMillis + state.remainingSeconds * 1000L,
            pausedAtMillis = null,
        )
    }

    /** Adds [seconds] to a running or paused block (the "give me ten more minutes" case). */
    fun extend(state: TimerState, nowMillis: Long, seconds: Int): TimerState {
        require(seconds > 0) { "seconds must be positive" }
        return when (state.phase) {
            TimerPhase.FOCUS, TimerPhase.BREAK -> {
                val remaining = remainingAt(state, nowMillis) + seconds
                state.copy(
                    plannedSeconds = state.plannedSeconds + seconds,
                    endsAtMillis = nowMillis + remaining * 1000L,
                    remainingSeconds = remaining,
                )
            }
            TimerPhase.PAUSED ->
                state.copy(
                    plannedSeconds = state.plannedSeconds + seconds,
                    remainingSeconds = state.remainingSeconds + seconds,
                )
            TimerPhase.BREAK_DUE, TimerPhase.IDLE -> state
        }
    }

    /**
     * Advances the clock. A focus block that reaches zero moves to [TimerPhase.BREAK_DUE] and counts
     * one completed block; a break that reaches zero returns to [TimerPhase.IDLE].
     */
    fun tick(state: TimerState, nowMillis: Long): TimerState {
        if (!state.isRunning) return state
        val remaining = remainingAt(state, nowMillis)
        if (remaining > 0) return state.copy(remainingSeconds = remaining)
        return when (state.phase) {
            TimerPhase.FOCUS -> {
                val endsAt = state.endsAtMillis ?: nowMillis
                // If the app was away when the block ended, the break must not auto-start on return.
                val sawItEnd = nowMillis - endsAt <= AWAY_GRACE_MILLIS
                state.copy(
                    phase = TimerPhase.BREAK_DUE,
                    endsAtMillis = null,
                    remainingSeconds = 0,
                    completedBlocksInCycle = state.completedBlocksInCycle + 1,
                    pausedAtMillis = null,
                    breakDueAtMillis = if (sawItEnd) nowMillis else null,
                )
            }
            else -> finishBreak(state)
        }
    }

    /** Recomputes the cached countdown of a running block from the wall clock; never changes the phase. */
    fun refresh(state: TimerState, nowMillis: Long): TimerState =
        if (state.isRunning) state.copy(remainingSeconds = remainingAt(state, nowMillis)) else state

    /** Seconds of the plan already spent at [nowMillis], whatever the cached countdown says. */
    fun elapsedAt(state: TimerState, nowMillis: Long): Int = refresh(state, nowMillis).elapsedSeconds

    /** Ends whatever is running and goes back to idle, keeping the cycle count. */
    fun stop(state: TimerState, nowMillis: Long): TimerState {
        val remaining = if (state.isRunning) remainingAt(state, nowMillis) else state.remainingSeconds
        return state.copy(
            phase = TimerPhase.IDLE,
            sessionId = null,
            taskId = null,
            endsAtMillis = null,
            remainingSeconds = remaining,
            pausedAtMillis = null,
            breakDueAtMillis = null,
        )
    }

    /** Skipping the eye-rest break still ends the cycle step, it just does not count as rest. */
    fun skipBreak(state: TimerState): TimerState =
        if (state.phase == TimerPhase.BREAK_DUE || state.phase == TimerPhase.BREAK) finishBreak(state) else state

    /** True when the next break should be the long one. */
    fun isLongBreakDue(state: TimerState, blocksBeforeLongBreak: Int): Boolean =
        blocksBeforeLongBreak > 0 && state.completedBlocksInCycle > 0 &&
            state.completedBlocksInCycle % blocksBeforeLongBreak == 0

    /** Seconds the user has been paused, derived from the wall clock. */
    fun pausedForSeconds(state: TimerState, nowMillis: Long): Int {
        val since = state.pausedAtMillis ?: return 0
        return ((nowMillis - since) / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * Seconds left before a due break starts on its own, or 0 when it will not (block expired while
     * away, or not in [TimerPhase.BREAK_DUE]).
     */
    fun autoStartSecondsLeft(state: TimerState, nowMillis: Long, delaySeconds: Int): Int {
        if (state.phase != TimerPhase.BREAK_DUE) return 0
        val due = state.breakDueAtMillis ?: return 0
        return (delaySeconds - ((nowMillis - due) / 1000L).toInt()).coerceIn(0, delaySeconds)
    }

    /**
     * True once the auto-start delay has just elapsed for a due break. A countdown nobody watched to
     * its end (window closed or process killed during it) is stale and never fires on return.
     */
    fun shouldAutoStartBreak(state: TimerState, nowMillis: Long, delaySeconds: Int): Boolean {
        if (state.phase != TimerPhase.BREAK_DUE) return false
        val due = state.breakDueAtMillis ?: return false
        val overdue = nowMillis - due - delaySeconds * 1000L
        return overdue in 0..AWAY_GRACE_MILLIS
    }

    private fun finishBreak(state: TimerState): TimerState =
        state.copy(
            phase = TimerPhase.IDLE,
            sessionId = null,
            taskId = null,
            plannedSeconds = 0,
            endsAtMillis = null,
            remainingSeconds = 0,
            pausedAtMillis = null,
            breakDueAtMillis = null,
        )

    private fun remainingAt(state: TimerState, nowMillis: Long): Int {
        val endsAt = state.endsAtMillis ?: return state.remainingSeconds
        val millisLeft = endsAt - nowMillis
        if (millisLeft <= 0) return 0
        // Round up so the display never shows 0 while a few hundred ms remain.
        return ((millisLeft + 999L) / 1000L).toInt()
    }

    private companion object {
        /** A block that ended more than this long ago ended while nobody was watching. */
        const val AWAY_GRACE_MILLIS = 5_000L
    }
}
