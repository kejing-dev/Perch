package io.embodit.perch.domain.model

enum class TimerPhase {
    /** Nothing running; the user is picking what to do next. */
    IDLE,
    /** A focus block is counting down. */
    FOCUS,
    /** A focus block is paused. */
    PAUSED,
    /** The focus block reached zero and the eye-rest break has not been started yet. */
    BREAK_DUE,
    /** A break is counting down. */
    BREAK,
}

/**
 * Pure timer state. Everything time-related is a wall-clock timestamp, so a paused app, a
 * suspended process, a closed window or a restart cannot drift the countdown, the paused-for
 * counter or the break auto-start.
 */
data class TimerState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val sessionId: String? = null,
    val taskId: String? = null,
    val plannedSeconds: Int = 0,
    /** Wall-clock time the running block ends; null unless FOCUS or BREAK. */
    val endsAtMillis: Long? = null,
    /** Seconds left when paused; also the last computed value while running. */
    val remainingSeconds: Int = 0,
    /** Focus blocks completed since the last long break. */
    val completedBlocksInCycle: Int = 0,
    /** When the current pause began; null unless PAUSED. */
    val pausedAtMillis: Long? = null,
    /**
     * When the block ended and the break became due, if the app was awake to see it; null when
     * the block expired while the app was away (then the break never auto-starts).
     */
    val breakDueAtMillis: Long? = null,
) {
    val isRunning: Boolean
        get() = phase == TimerPhase.FOCUS || phase == TimerPhase.BREAK

    val elapsedSeconds: Int
        get() = (plannedSeconds - remainingSeconds).coerceIn(0, plannedSeconds)

    /** 0f..1f fraction of the planned block that has elapsed. */
    val progress: Float
        get() = if (plannedSeconds <= 0) 0f else elapsedSeconds.toFloat() / plannedSeconds.toFloat()
}

/** Minutes for display and logging: nearest minute, used everywhere so figures agree. */
fun secondsToMinutes(seconds: Int): Int = (seconds.coerceAtLeast(0) + 30) / 60
