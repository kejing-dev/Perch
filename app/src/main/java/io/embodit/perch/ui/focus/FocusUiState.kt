package io.embodit.perch.ui.focus

import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome

/** The Perch card's state, named from the metaphor (see design-doc.md §3 state graph). */
enum class PerchPhase { EMPTY, ROOSTING, FOCUSING, PAUSED, LANDING, RESTING }

data class TaskUi(val id: String, val title: String)

/** One block drawn on the day-wire time axis. */
data class WireBlockUi(
    val id: String,
    val startMinuteOfDay: Int,
    val minutes: Int,
    val kind: SessionKind,
    val outcome: SessionOutcome,
    val title: String,
)

enum class LogTab { TODAY, YESTERDAY, WEEK, QUEUE }

data class LogRowUi(
    val id: String,
    val title: String,
    val startedAtMillis: Long,
    val minutes: Int,
    val kind: SessionKind,
    val outcome: SessionOutcome,
)

sealed interface PerchDialog {
    data class FinishEarly(val minutesLogged: Int, val taskTitle: String) : PerchDialog
    data class DeleteTask(val task: TaskUi) : PerchDialog
}

data class FocusUiState(
    val phase: PerchPhase = PerchPhase.EMPTY,
    val perched: TaskUi? = null,
    val queue: List<TaskUi> = emptyList(),
    /** Every open task in order (perched first); the Queue tab of the log sheet lists it. */
    val openTasks: List<TaskUi> = emptyList(),
    val draft: String = "",
    // Timer
    val remainingSeconds: Int = 0,
    val plannedSeconds: Int = 0,
    val progress: Float = 0f,
    val blockIndexInCycle: Int = 1,
    val blocksPerCycle: Int = 4,
    val blockStartedAtMillis: Long? = null,
    /** Title of the task the running block is logged for (differs from [perched] after land-and-hop). */
    val blockTaskTitle: String? = null,
    val pausedForSeconds: Int = 0,
    val landingAutoStartSeconds: Int = 0,
    val nextBreakIsLong: Boolean = false,
    /** The task landed through the break card's chip (the chip then offers to undo exactly it). */
    val breakLandedTaskId: String? = null,
    // Day
    val focusMinutesToday: Int = 0,
    val completedBlocksToday: Int = 0,
    val landedTasksToday: Int = 0,
    val wireBlocks: List<WireBlockUi> = emptyList(),
    val nowMinuteOfDay: Int = 0,
    val dayStartMinute: Int = 9 * 60,
    val dayEndMinute: Int = 18 * 60,
    val goalMinutes: Int = 180,
    // Settings + overlays
    val settings: FocusSettings = FocusSettings(),
    val selectedFocusMinutes: Int = 25,
    val showDurations: Boolean = false,
    val showLog: Boolean = false,
    val logTab: LogTab = LogTab.TODAY,
    val logRows: List<LogRowUi> = emptyList(),
    val logFocusMinutes: Int = 0,
    val dialog: PerchDialog? = null,
    val showCoachmark: Boolean = false,
    val loadError: Boolean = false,
) {
    val perchedLandedDuringBreak: Boolean
        get() = breakLandedTaskId != null

    val isBlockActive: Boolean
        get() = phase == PerchPhase.FOCUSING || phase == PerchPhase.PAUSED

    val isBreakPhase: Boolean
        get() = phase == PerchPhase.LANDING || phase == PerchPhase.RESTING

    val goalProgress: Float
        get() = if (goalMinutes <= 0) 0f else (focusMinutesToday.toFloat() / goalMinutes).coerceIn(0f, 1f)

    val canExtend: Boolean
        get() = isBlockActive && remainingSeconds <= 5 * 60
}

sealed interface FocusEvent {
    data class DraftChanged(val text: String) : FocusEvent
    data object AddTask : FocusEvent
    data object Start : FocusEvent
    data object Pause : FocusEvent
    data object Resume : FocusEvent
    data object LandPerched : FocusEvent
    data class Extend(val seconds: Int) : FocusEvent
    data object FinishEarlyRequested : FocusEvent
    data object FinishEarlyConfirmed : FocusEvent
    data object StartBreak : FocusEvent
    data object SkipBreak : FocusEvent
    data object BackEarly : FocusEvent
    data class PerchNext(val taskId: String) : FocusEvent
    /** Perches the task after the current one and re-points the running block at it (More menu). */
    data object SwitchToNext : FocusEvent
    data class LandTask(val taskId: String) : FocusEvent
    data class MoveTask(val taskId: String, val delta: Int) : FocusEvent
    data class DeleteTaskRequested(val taskId: String) : FocusEvent
    data object DeleteTaskConfirmed : FocusEvent
    /** Undo of the most recent landing (debug tooling). */
    data object UndoLastLanding : FocusEvent
    /** Undo of one specific landing (the snackbar that named it, or the break card chip). */
    data class UndoLanding(val taskId: String) : FocusEvent
    data class SelectFocusMinutes(val minutes: Int) : FocusEvent
    data class UpdateSettings(val settings: FocusSettings) : FocusEvent
    data class ShowDurations(val show: Boolean) : FocusEvent
    data object OpenLog : FocusEvent
    /** Opens the log sheet on the Queue tab (overflow rows beyond the visible queue). */
    data object OpenQueue : FocusEvent
    data object CloseLog : FocusEvent
    data class SelectLogTab(val tab: LogTab) : FocusEvent
    data object DismissCoachmark : FocusEvent
    data object DismissDialog : FocusEvent

    /** Shifts the running block's wall-clock end so tests and debug tooling can fast-forward. */
    data class AdvanceClockForTesting(val seconds: Int) : FocusEvent
}

/** One-shot effects the screen reacts to (snackbars, chimes). */
sealed interface FocusEffect {
    data class Landed(val taskId: String, val title: String) : FocusEffect
    data class Logged(val minutes: Int) : FocusEffect
    /** The up-arrow lined a task up behind the perched one because a block or break is on. */
    data class NextUp(val title: String) : FocusEffect
    data object BlockEnded : FocusEffect
    data object BreakEnded : FocusEffect
    data object LoadFailed : FocusEffect
}
