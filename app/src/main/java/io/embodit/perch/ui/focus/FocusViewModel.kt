package io.embodit.perch.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.embodit.perch.data.repository.PerchRepository
import io.embodit.perch.data.repository.PerchSnapshot
import io.embodit.perch.data.session.FocusSessionStore
import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.domain.model.Task
import io.embodit.perch.domain.model.TimerPhase
import io.embodit.perch.domain.model.TimerState
import io.embodit.perch.domain.model.secondsToMinutes
import io.embodit.perch.domain.usecase.FocusTimerUseCase
import io.embodit.perch.domain.usecase.TaskListUseCase
import io.embodit.perch.domain.usecase.TodaySummaryUseCase
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** UI-only state that is not derived from the repository or the timer. */
internal data class FocusLocalState(
    val draft: String = "",
    val dialog: PerchDialog? = null,
    val showDurations: Boolean = false,
    val showLog: Boolean = false,
    val logTab: LogTab = LogTab.TODAY,
    val lastLandedTaskId: String? = null,
    /** The task landed through the break card's chip; that chip undoes exactly this one. */
    val breakLandedTaskId: String? = null,
    val loadError: Boolean = false,
    /** Last ticker time; bumped on every tick so clock-derived fields recompute while paused or idle. */
    val tickMillis: Long = 0L,
)

class FocusViewModel(
    private val repository: PerchRepository,
    private val store: FocusSessionStore,
    private val timer: FocusTimerUseCase = FocusTimerUseCase(),
    private val taskList: TaskListUseCase = TaskListUseCase(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val summary: TodaySummaryUseCase = TodaySummaryUseCase(timeZone),
    externalEvents: Flow<FocusEvent>? = null,
) : ViewModel() {

    private val local = MutableStateFlow(FocusLocalState())
    private val effectChannel = Channel<FocusEffect>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects: Flow<FocusEffect> = effectChannel.receiveAsFlow()

    /** Serializes every block transition (user events and the ticker) so none can interleave. */
    private val transitions = Mutex()

    val state: StateFlow<FocusUiState> =
        combine(repository.snapshot, store.timer, local) { snapshot, timerState, localState ->
            reduce(snapshot, timerState, localState, clock())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FocusUiState())

    init {
        viewModelScope.launch { reconcileOrphanSessions() }
        externalEvents?.let { source -> viewModelScope.launch { source.collect { onEvent(it) } } }
        viewModelScope.launch {
            // A plain loop: nothing it does can cancel the tick that is running.
            // It ticks before its first delay so a restored or re-opened block never shows a stale clock.
            var idleTicks = 0
            while (isActive) {
                if (store.timer.value.phase != TimerPhase.IDLE) {
                    idleTicks = 0
                    tick()
                } else if (++idleTicks % IDLE_REFRESH_TICKS == 0) {
                    local.update { it.copy(tickMillis = clock()) }
                }
                delay(TICK_MILLIS)
            }
        }
    }

    fun onEvent(event: FocusEvent) {
        when (event) {
            is FocusEvent.DraftChanged -> local.update { it.copy(draft = event.text) }
            FocusEvent.AddTask -> {
                val draft = local.value.draft
                launch { addTask(draft) }
            }
            FocusEvent.Start -> launch { startFocus() }
            FocusEvent.Pause -> launch { pause() }
            FocusEvent.Resume -> launch { resume() }
            FocusEvent.LandPerched -> launch { landPerched() }
            is FocusEvent.Extend -> launch { extend(event.seconds) }
            FocusEvent.FinishEarlyRequested -> requestFinishEarly()
            FocusEvent.FinishEarlyConfirmed -> launch { finishEarly() }
            FocusEvent.StartBreak -> launch { transitions.withLock { startBreak() } }
            FocusEvent.SkipBreak -> launch { skipBreak() }
            FocusEvent.BackEarly -> launch { backEarly() }
            is FocusEvent.PerchNext -> launch { perchNext(event.taskId) }
            FocusEvent.SwitchToNext -> launch { switchToNext() }
            is FocusEvent.LandTask -> launch { landTask(event.taskId) }
            is FocusEvent.MoveTask -> launch { moveTask(event.taskId, event.delta) }
            is FocusEvent.DeleteTaskRequested -> requestDelete(event.taskId)
            FocusEvent.DeleteTaskConfirmed -> launch { deleteTask() }
            FocusEvent.UndoLastLanding -> launch { local.value.lastLandedTaskId?.let { undoLanding(it) } }
            is FocusEvent.UndoLanding -> launch { undoLanding(event.taskId) }
            is FocusEvent.SelectFocusMinutes -> launch { selectFocusMinutes(event.minutes) }
            is FocusEvent.UpdateSettings -> launch { repository.updateSettings(event.settings) }
            is FocusEvent.ShowDurations -> local.update { it.copy(showDurations = event.show) }
            FocusEvent.OpenLog -> local.update { it.copy(showLog = true, logTab = if (it.logTab == LogTab.QUEUE) LogTab.TODAY else it.logTab) }
            FocusEvent.OpenQueue -> local.update { it.copy(showLog = true, logTab = LogTab.QUEUE) }
            FocusEvent.CloseLog -> local.update { it.copy(showLog = false) }
            is FocusEvent.SelectLogTab -> local.update { it.copy(logTab = event.tab) }
            FocusEvent.DismissCoachmark -> launch { dismissCoachmark() }
            FocusEvent.DismissDialog -> local.update { it.copy(dialog = null) }
            is FocusEvent.AdvanceClockForTesting -> launch { advanceClock(event.seconds) }
        }
    }

    // ---------------------------------------------------------------- reducers

    private fun reduce(snapshot: PerchSnapshot, t: TimerState, l: FocusLocalState, now: Long): FocusUiState {
        val settings = snapshot.settings
        val open = taskList.openTasks(snapshot.tasks)
        val perched = open.firstOrNull()?.let { TaskUi(it.id, it.title) }
        val queue = open.drop(1).map { TaskUi(it.id, it.title) }
        val phase = when (t.phase) {
            TimerPhase.IDLE -> if (open.isEmpty()) PerchPhase.EMPTY else PerchPhase.ROOSTING
            TimerPhase.FOCUS -> PerchPhase.FOCUSING
            TimerPhase.PAUSED -> PerchPhase.PAUSED
            TimerPhase.BREAK_DUE -> PerchPhase.LANDING
            TimerPhase.BREAK -> PerchPhase.RESTING
        }
        val today = summary.summarize(snapshot.sessions, snapshot.tasks, now)
        val runningSession = t.sessionId?.let { id -> snapshot.sessions.firstOrNull { it.id == id } }
        val runningElapsed = if (t.phase == TimerPhase.FOCUS || t.phase == TimerPhase.PAUSED) t.elapsedSeconds else 0
        val focusSecondsToday = today.focusSeconds + runningElapsed
        val dayStart = summary.startOfDay(now)
        val wire = today.sessionsToday.filterNot(::isNegligibleBreak).map { s ->
            val elapsed = if (s.id == t.sessionId && s.outcome == SessionOutcome.RUNNING) t.elapsedSeconds else s.elapsedSeconds
            WireBlockUi(
                id = s.id,
                startMinuteOfDay = ((s.startedAtMillis - dayStart) / 60_000L).toInt().coerceIn(0, 24 * 60),
                minutes = secondsToMinutes(elapsed),
                kind = s.kind,
                outcome = s.outcome,
                title = s.taskTitle,
            )
        }
        val nowMinute = ((now - dayStart) / 60_000L).toInt().coerceIn(0, 24 * 60)
        val blocksPerCycle = settings.blocksBeforeLongBreak
        val (rows, rowsMinutes) = logRows(snapshot.sessions, l.logTab, now)
        return FocusUiState(
            phase = phase,
            perched = perched,
            queue = queue,
            openTasks = open.map { TaskUi(it.id, it.title) },
            draft = l.draft,
            remainingSeconds = t.remainingSeconds,
            plannedSeconds = t.plannedSeconds,
            progress = t.progress,
            blockIndexInCycle = (t.completedBlocksInCycle % blocksPerCycle) + 1,
            blocksPerCycle = blocksPerCycle,
            blockStartedAtMillis = runningSession?.startedAtMillis,
            blockTaskTitle = runningSession?.taskTitle?.takeIf { it.isNotBlank() },
            pausedForSeconds = timer.pausedForSeconds(t, now),
            landingAutoStartSeconds = if (settings.autoStartBreak) timer.autoStartSecondsLeft(t, now, LANDING_AUTO_START_SECONDS) else 0,
            nextBreakIsLong = timer.isLongBreakDue(t, blocksPerCycle),
            breakLandedTaskId = l.breakLandedTaskId?.takeIf { id -> snapshot.tasks.any { it.id == id && it.isDone } },
            focusMinutesToday = secondsToMinutes(focusSecondsToday),
            completedBlocksToday = today.completedBlocks,
            landedTasksToday = today.completedTasks,
            wireBlocks = wire,
            nowMinuteOfDay = nowMinute,
            // The axis covers the work hours, now, and every block of today, on whole hours.
            dayStartMinute = minOf(
                settings.dayStartHour * 60,
                (nowMinute / 60) * 60,
                wire.minOfOrNull { (it.startMinuteOfDay / 60) * 60 } ?: Int.MAX_VALUE,
            ),
            dayEndMinute = maxOf(
                settings.dayEndHour * 60,
                ((nowMinute / 60) + 1) * 60,
                wire.maxOfOrNull { ((it.startMinuteOfDay + it.minutes + 59) / 60) * 60 } ?: 0,
            ).coerceAtMost(24 * 60),
            goalMinutes = settings.goalMinutes,
            settings = settings,
            selectedFocusMinutes = settings.focusMinutes,
            showDurations = l.showDurations,
            showLog = l.showLog,
            logTab = l.logTab,
            logRows = rows,
            logFocusMinutes = rowsMinutes,
            // The finish dialog always states what would be logged right now.
            dialog = (l.dialog as? PerchDialog.FinishEarly)?.copy(minutesLogged = secondsToMinutes(t.elapsedSeconds)) ?: l.dialog,
            showCoachmark = !settings.hasSeenCoachmark && open.isNotEmpty(),
            loadError = l.loadError,
        )
    }

    private fun logRows(sessions: List<FocusSession>, tab: LogTab, now: Long): Pair<List<LogRowUi>, Int> {
        if (tab == LogTab.QUEUE) return emptyList<LogRowUi>() to 0
        val dayStart = summary.startOfDay(now)
        val (from, to) = when (tab) {
            LogTab.TODAY -> dayStart to summary.nextDayStart(dayStart)
            LogTab.YESTERDAY -> summary.daysBefore(dayStart, 1) to dayStart
            else -> summary.daysBefore(dayStart, 6) to summary.nextDayStart(dayStart)
        }
        val inRange = sessions.filter {
            it.startedAtMillis in from until to && it.outcome != SessionOutcome.RUNNING && !isNegligibleBreak(it)
        }
        val rows = inRange
            .sortedByDescending { it.startedAtMillis }
            .map { LogRowUi(it.id, it.taskTitle, it.startedAtMillis, secondsToMinutes(it.elapsedSeconds), it.kind, it.outcome) }
        val minutes = secondsToMinutes(inRange.filter { it.kind == SessionKind.FOCUS }.sumOf { it.elapsedSeconds })
        return rows to minutes
    }

    /** A break that was cut off before it rounded to a minute is noise in the log and on the wire. */
    private fun isNegligibleBreak(session: FocusSession): Boolean =
        session.kind == SessionKind.BREAK && session.outcome == SessionOutcome.STOPPED_EARLY &&
            secondsToMinutes(session.elapsedSeconds) == 0

    // ---------------------------------------------------------------- task events

    private suspend fun addTask(raw: String) {
        val title = taskList.normalizeTitle(raw) ?: return
        val tasks = repository.snapshot.value.tasks
        val order = (tasks.maxOfOrNull { it.sortOrder } ?: -1) + 1
        repository.addTask(Task(id = newId(), title = title, createdAtMillis = clock(), sortOrder = order))
        local.update { if (it.draft == raw) it.copy(draft = "") else it }
    }

    /**
     * The queue's up-arrow. Normally the task takes the perch. While the perched task is the one
     * being worked on (a block is running, or its break is on and it has not been landed yet), the
     * promoted task lines up right behind it instead.
     */
    private suspend fun perchNext(taskId: String) = transitions.withLock {
        val keepPerched = when (store.timer.value.phase) {
            TimerPhase.FOCUS, TimerPhase.PAUSED -> true
            TimerPhase.BREAK_DUE, TimerPhase.BREAK -> local.value.breakLandedTaskId == null
            TimerPhase.IDLE -> false
        }
        val before = taskList.openTasks(repository.snapshot.value.tasks)
        repository.replaceTasks(taskList.promoteToNext(repository.snapshot.value.tasks, taskId, keepPerched))
        val target = before.firstOrNull { it.id == taskId }
        if (keepPerched && target != null && before.firstOrNull()?.id != taskId) {
            effectChannel.trySend(FocusEffect.NextUp(target.title))
        }
    }

    /**
     * "Switch to next task" in the More menu: a deliberate "this block is about that now", so the
     * running session is re-pointed and the card, the finish dialog and the log agree. (Landing the
     * perched task mid-block is different: the block stays logged for the task it finished.)
     */
    private suspend fun switchToNext() = transitions.withLock {
        val next = taskList.openTasks(repository.snapshot.value.tasks).getOrNull(1) ?: return@withLock
        repository.replaceTasks(taskList.promoteToNext(repository.snapshot.value.tasks, next.id))
        val t = store.timer.value
        if (t.phase != TimerPhase.FOCUS && t.phase != TimerPhase.PAUSED) return@withLock
        val session = repository.snapshot.value.sessions
            .firstOrNull { it.id == t.sessionId && it.outcome == SessionOutcome.RUNNING } ?: return@withLock
        store.update { it.copy(taskId = next.id) }
        repository.upsertSession(session.copy(taskId = next.id, taskTitle = next.title))
    }

    private suspend fun moveTask(taskId: String, delta: Int) {
        repository.replaceTasks(taskList.move(repository.snapshot.value.tasks, taskId, delta))
    }

    private suspend fun landPerched() {
        val perched = taskList.nextTask(repository.snapshot.value.tasks) ?: return
        landTask(perched.id)
    }

    private suspend fun landTask(taskId: String) {
        val task = repository.snapshot.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (task.isDone) return
        val phase = store.timer.value.phase
        val duringBreak = phase == TimerPhase.BREAK_DUE || phase == TimerPhase.BREAK
        // Landing the perched task during its break (chip or checkbox) is what the break chip shows and undoes.
        val landsBreakTask = duringBreak && local.value.breakLandedTaskId == null &&
            taskList.nextTask(repository.snapshot.value.tasks)?.id == task.id
        repository.updateTask(task.copy(completedAtMillis = clock()))
        local.update { it.copy(lastLandedTaskId = task.id, breakLandedTaskId = if (landsBreakTask) task.id else it.breakLandedTaskId) }
        effectChannel.trySend(FocusEffect.Landed(task.id, task.title))
    }

    private suspend fun undoLanding(taskId: String) {
        val task = repository.snapshot.value.tasks.firstOrNull { it.id == taskId } ?: return
        if (!task.isDone) return
        repository.updateTask(task.copy(completedAtMillis = null))
        local.update {
            it.copy(
                lastLandedTaskId = if (it.lastLandedTaskId == taskId) null else it.lastLandedTaskId,
                breakLandedTaskId = if (it.breakLandedTaskId == taskId) null else it.breakLandedTaskId,
            )
        }
    }

    private fun requestDelete(taskId: String) {
        val task = repository.snapshot.value.tasks.firstOrNull { it.id == taskId } ?: return
        local.update { it.copy(dialog = PerchDialog.DeleteTask(TaskUi(task.id, task.title))) }
    }

    private suspend fun deleteTask() {
        val dialog = local.value.dialog as? PerchDialog.DeleteTask ?: return
        repository.deleteTask(dialog.task.id)
        local.update { it.copy(dialog = null) }
    }

    private suspend fun selectFocusMinutes(minutes: Int) {
        val settings = repository.snapshot.value.settings
        repository.updateSettings(settings.copy(focusMinutes = minutes))
    }

    private suspend fun dismissCoachmark() {
        val settings = repository.snapshot.value.settings
        if (!settings.hasSeenCoachmark) repository.updateSettings(settings.copy(hasSeenCoachmark = true))
    }

    // ---------------------------------------------------------------- block events

    private suspend fun startFocus() = transitions.withLock { startFocusLocked() }

    private suspend fun pause() = transitions.withLock {
        val now = clock()
        // A block that already reached zero ends through the normal path instead of pausing.
        tickLocked(now)
        store.update { timer.pause(it, now) }
    }

    private suspend fun resume() = transitions.withLock {
        store.update { timer.resume(it, clock()) }
    }

    private suspend fun extend(seconds: Int) = transitions.withLock {
        val now = clock()
        tickLocked(now)
        val before = store.timer.value
        val updated = store.update { timer.extend(it, now, seconds) }
        if (updated.plannedSeconds == before.plannedSeconds) return@withLock
        val session = updated.sessionId?.let { id -> repository.snapshot.value.sessions.firstOrNull { it.id == id } } ?: return@withLock
        repository.upsertSession(session.copy(plannedSeconds = updated.plannedSeconds))
    }

    private fun requestFinishEarly() {
        val t = store.timer.value
        if (t.phase != TimerPhase.FOCUS && t.phase != TimerPhase.PAUSED) return
        val title = t.sessionId?.let { id -> repository.snapshot.value.sessions.firstOrNull { it.id == id }?.taskTitle }.orEmpty()
        local.update { it.copy(dialog = PerchDialog.FinishEarly(minutesLogged = secondsToMinutes(t.elapsedSeconds), taskTitle = title)) }
    }

    private suspend fun finishEarly() = transitions.withLock {
        val now = clock()
        tickLocked(now)
        val t = store.timer.value
        if (t.phase != TimerPhase.FOCUS && t.phase != TimerPhase.PAUSED) {
            local.update { it.copy(dialog = null) }
            return@withLock
        }
        val elapsed = t.elapsedSeconds
        // Leave the running phase before touching the session so the ticker cannot complete it too.
        store.update { timer.stop(it, now) }
        local.update { it.copy(dialog = null, showDurations = false) }
        closeSession(t.sessionId, elapsed, SessionOutcome.STOPPED_EARLY, now)
        effectChannel.trySend(FocusEffect.Logged(secondsToMinutes(elapsed)))
    }

    /** Caller holds [transitions]. */
    private suspend fun startBreak() {
        val t = store.timer.value
        if (t.phase != TimerPhase.BREAK_DUE) return
        val settings = repository.snapshot.value.settings
        val minutes = if (timer.isLongBreakDue(t, settings.blocksBeforeLongBreak)) settings.longBreakMinutes else settings.breakMinutes
        val now = clock()
        val session = FocusSession(
            id = newId(),
            kind = SessionKind.BREAK,
            taskId = null,
            taskTitle = "",
            startedAtMillis = now,
            plannedSeconds = minutes * 60,
        )
        store.update { timer.startBreak(it, now, session.id, minutes * 60) }
        repository.upsertSession(session)
    }

    private suspend fun skipBreak() = transitions.withLock {
        val now = clock()
        tickLocked(now)
        val t = store.timer.value
        if (t.phase != TimerPhase.BREAK_DUE && t.phase != TimerPhase.BREAK) return@withLock
        val elapsed = if (t.phase == TimerPhase.BREAK) t.elapsedSeconds else 0
        store.update { timer.skipBreak(it) }
        local.update { it.copy(breakLandedTaskId = null) }
        if (t.phase == TimerPhase.BREAK) closeSession(t.sessionId, elapsed, SessionOutcome.STOPPED_EARLY, now)
        if (taskList.nextTask(repository.snapshot.value.tasks) != null) startFocusLocked()
    }

    private suspend fun backEarly() = transitions.withLock {
        val now = clock()
        tickLocked(now)
        val t = store.timer.value
        if (t.phase != TimerPhase.BREAK) return@withLock
        val elapsed = t.elapsedSeconds
        store.update { timer.stop(it, now) }
        local.update { it.copy(breakLandedTaskId = null) }
        closeSession(t.sessionId, elapsed, SessionOutcome.STOPPED_EARLY, now)
    }

    /** [startFocus] body for callers that already hold [transitions]. */
    private suspend fun startFocusLocked() {
        if (store.timer.value.phase != TimerPhase.IDLE) return
        val snapshot = repository.snapshot.value
        val perched = taskList.nextTask(snapshot.tasks) ?: return
        val now = clock()
        val seconds = snapshot.settings.focusMinutes * 60
        val session = FocusSession(
            id = newId(),
            kind = SessionKind.FOCUS,
            taskId = perched.id,
            taskTitle = perched.title,
            startedAtMillis = now,
            plannedSeconds = seconds,
        )
        store.update { timer.startFocus(it, now, session.id, perched.id, seconds) }
        local.update { it.copy(breakLandedTaskId = null, showDurations = false) }
        repository.upsertSession(session)
    }

    private suspend fun tick() = transitions.withLock { tickLocked(clock()) }

    /** Brings the store up to [now] and does what a phase change implies. Caller holds [transitions]. */
    private suspend fun tickLocked(now: Long) {
        val before = store.timer.value
        val after = store.update { timer.tick(it, now) }
        val phaseChanged = before.phase != after.phase
        local.update {
            it.copy(
                tickMillis = now,
                showDurations = if (phaseChanged) false else it.showDurations,
                // "End this block early?" makes no sense once the block ended by itself.
                dialog = if (phaseChanged && it.dialog is PerchDialog.FinishEarly) null else it.dialog,
            )
        }
        when {
            before.phase == TimerPhase.FOCUS && after.phase == TimerPhase.BREAK_DUE -> {
                effectChannel.trySend(FocusEffect.BlockEnded)
                closeSession(before.sessionId, before.plannedSeconds, SessionOutcome.COMPLETED, now)
            }
            before.phase == TimerPhase.BREAK && after.phase == TimerPhase.IDLE -> {
                local.update { it.copy(breakLandedTaskId = null) }
                effectChannel.trySend(FocusEffect.BreakEnded)
                closeSession(before.sessionId, before.plannedSeconds, SessionOutcome.COMPLETED, now)
            }
            after.phase == TimerPhase.BREAK_DUE -> {
                val auto = repository.snapshot.value.settings.autoStartBreak
                if (auto && timer.shouldAutoStartBreak(after, now, LANDING_AUTO_START_SECONDS)) startBreak()
            }
        }
    }

    private suspend fun advanceClock(seconds: Int) {
        if (seconds <= 0) return
        val shift = seconds * 1000L
        // One atomic store update; the lock is taken by tick() itself.
        store.update { t ->
            t.copy(
                endsAtMillis = t.endsAtMillis?.minus(shift),
                pausedAtMillis = t.pausedAtMillis?.minus(shift),
                breakDueAtMillis = t.breakDueAtMillis?.minus(shift),
            )
        }
        tick()
    }

    private suspend fun closeSession(sessionId: String?, elapsedSeconds: Int, outcome: SessionOutcome, now: Long) {
        val session = sessionId?.let { id -> repository.snapshot.value.sessions.firstOrNull { it.id == id } } ?: return
        if (session.outcome != SessionOutcome.RUNNING) return
        repository.upsertSession(
            session.copy(
                elapsedSeconds = elapsedSeconds.coerceIn(0, session.plannedSeconds),
                endedAtMillis = now,
                outcome = outcome,
            ),
        )
    }

    /** Sessions left RUNNING by a previous process get closed with the best available estimate. */
    private suspend fun reconcileOrphanSessions() {
        repository.isLoaded.first { it }
        if (repository.loadError.value) {
            local.update { it.copy(loadError = true) }
            effectChannel.trySend(FocusEffect.LoadFailed)
        }
        val now = clock()
        val live = store.timer.value
        val liveId = live.sessionId
        val liveNeedsRecord = live.phase == TimerPhase.FOCUS || live.phase == TimerPhase.PAUSED || live.phase == TimerPhase.BREAK
        if (liveId != null && liveNeedsRecord && repository.snapshot.value.sessions.none { it.id == liveId }) {
            // The timer survived but its session record did not (corrupt file, or killed between the two
            // writes at Start): rebuild the record so the block is still logged when it ends.
            val task = repository.snapshot.value.tasks.firstOrNull { it.id == live.taskId }
            val elapsed = timer.elapsedAt(live, now)
            repository.upsertSession(
                FocusSession(
                    id = liveId,
                    kind = if (live.phase == TimerPhase.BREAK) SessionKind.BREAK else SessionKind.FOCUS,
                    taskId = task?.id,
                    taskTitle = task?.title.orEmpty(),
                    startedAtMillis = now - elapsed * 1000L,
                    plannedSeconds = live.plannedSeconds,
                ),
            )
        }
        repository.snapshot.value.sessions
            .filter { it.outcome == SessionOutcome.RUNNING && it.id != liveId }
            .forEach { s ->
                val elapsed = ((now - s.startedAtMillis) / 1000L).toInt().coerceIn(0, s.plannedSeconds)
                val outcome = if (elapsed >= s.plannedSeconds) SessionOutcome.COMPLETED else SessionOutcome.STOPPED_EARLY
                repository.upsertSession(s.copy(elapsedSeconds = elapsed, endedAtMillis = s.startedAtMillis + elapsed * 1000L, outcome = outcome))
            }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    class Factory(
        private val repository: PerchRepository,
        private val store: FocusSessionStore,
        private val externalEvents: Flow<FocusEvent>? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FocusViewModel(repository = repository, store = store, externalEvents = externalEvents) as T
    }

    private companion object {
        const val TICK_MILLIS = 1000L
        /** While idle only the day wire changes; refresh it every 30 s. */
        const val IDLE_REFRESH_TICKS = 30
        const val LANDING_AUTO_START_SECONDS = 20
    }
}
