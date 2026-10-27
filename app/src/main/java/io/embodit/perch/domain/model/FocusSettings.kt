package io.embodit.perch.domain.model

/** User-tunable durations and day layout. Minutes, because that is how people think about focus blocks. */
data class FocusSettings(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val blocksBeforeLongBreak: Int = 4,
    val autoStartBreak: Boolean = true,
    /** Daily focus goal shown on the day wire. */
    val goalMinutes: Int = 180,
    /** Work hours drawn on the day wire (24 h clock, end exclusive). */
    val dayStartHour: Int = 9,
    val dayEndHour: Int = 18,
    val hasSeenCoachmark: Boolean = false,
) {
    companion object {
        /** Presets offered as one-tap choices in the UI. */
        val FOCUS_PRESETS_MINUTES: List<Int> = listOf(25, 45, 90)
        const val MIN_FOCUS_MINUTES = 5
        const val MAX_FOCUS_MINUTES = 180
        const val MIN_BREAK_MINUTES = 1
        const val MAX_BREAK_MINUTES = 60
        const val MIN_GOAL_MINUTES = 30
        const val MAX_GOAL_MINUTES = 600
    }

    fun clamped(): FocusSettings {
        val start = dayStartHour.coerceIn(0, 22)
        val end = dayEndHour.coerceIn(start + 1, 24)
        return copy(
            focusMinutes = focusMinutes.coerceIn(MIN_FOCUS_MINUTES, MAX_FOCUS_MINUTES),
            breakMinutes = breakMinutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES),
            longBreakMinutes = longBreakMinutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES),
            blocksBeforeLongBreak = blocksBeforeLongBreak.coerceIn(1, 12),
            goalMinutes = goalMinutes.coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES),
            dayStartHour = start,
            dayEndHour = end,
        )
    }
}
