package io.embodit.perch.data.session

import io.embodit.perch.domain.model.TimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide holder for the live timer. It outlives the window's ViewModel so closing and
 * reopening the Perch window does not lose a running block; remaining time is derived from the
 * wall clock, so no ticking happens here.
 */
class FocusSessionStore(initial: TimerState = TimerState()) {
    private val state = MutableStateFlow(initial)
    val timer: StateFlow<TimerState> = state

    fun update(transform: (TimerState) -> TimerState): TimerState {
        state.update(transform)
        return state.value
    }
}
