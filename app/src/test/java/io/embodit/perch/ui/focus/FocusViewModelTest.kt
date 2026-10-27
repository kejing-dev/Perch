package io.embodit.perch.ui.focus

import androidx.lifecycle.viewModelScope
import io.embodit.perch.data.repository.FakePerchRepository
import io.embodit.perch.data.repository.PerchSnapshot
import io.embodit.perch.data.session.FocusSessionStore
import io.embodit.perch.domain.model.FocusSession
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.domain.model.Task
import io.embodit.perch.domain.model.TimerPhase
import io.embodit.perch.domain.model.TimerState
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var now = 1_790_244_000_000L // 2026-09-25T10:00:00Z
    private var ids = 0
    private lateinit var repository: FakePerchRepository
    private lateinit var store: FocusSessionStore
    private val created = mutableListOf<FocusViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakePerchRepository(PerchSnapshot(settings = FocusSettings(focusMinutes = 25, breakMinutes = 5)))
        store = FocusSessionStore()
    }

    @After
    fun tearDown() {
        // The 1 s ticker lives in viewModelScope; cancel it so runTest's final drain terminates.
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() = FocusViewModel(
        repository = repository,
        store = store,
        clock = { now },
        newId = { "id-${ids++}" },
        timeZone = TimeZone.getTimeZone("UTC"),
    ).also { created += it }

    /**
     * runTest drains the scheduler until idle after the body; the ViewModel's periodic ticker would
     * keep it busy forever, so every ViewModel scope is cancelled before the body returns.
     */
    private fun runPerchTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
            created.clear()
        }
    }

    /** Advances both the virtual dispatcher clock and the ViewModel's wall clock together. */
    private fun TestScope.passSeconds(seconds: Int) {
        repeat(seconds) {
            now += 1000L
            advanceTimeBy(1000L)
            runCurrent()
        }
    }

    @Test
    fun `starts empty and becomes roosting when a task is added`() = runPerchTest {
        val vm = viewModel()
        runCurrent()
        assertEquals(PerchPhase.EMPTY, vm.state.value.phase)

        vm.onEvent(FocusEvent.DraftChanged("  Write the brief  "))
        vm.onEvent(FocusEvent.AddTask)
        runCurrent()

        val state = vm.state.value
        assertEquals(PerchPhase.ROOSTING, state.phase)
        assertEquals("Write the brief", state.perched?.title)
        assertEquals("", state.draft)
        assertTrue(state.queue.isEmpty())
    }

    @Test
    fun `start runs a focus block on the perched task and ticks from the wall clock`() = runPerchTest {
        val vm = viewModel()
        vm.onEvent(FocusEvent.DraftChanged("Task A"))
        vm.onEvent(FocusEvent.AddTask)
        runCurrent()

        vm.onEvent(FocusEvent.Start)
        runCurrent()
        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)
        assertEquals(25 * 60, vm.state.value.remainingSeconds)
        val session = repository.snapshot.value.sessions.single()
        assertEquals(SessionKind.FOCUS, session.kind)
        assertEquals("Task A", session.taskTitle)
        assertEquals(SessionOutcome.RUNNING, session.outcome)

        passSeconds(90)
        assertEquals(25 * 60 - 90, vm.state.value.remainingSeconds)
        assertEquals(2, vm.state.value.focusMinutesToday) // 90 s rounds to 2 min
    }

    @Test
    fun `block reaching zero lands the card and logs a completed session`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        vm.onEvent(FocusEvent.DraftChanged("Task A"))
        vm.onEvent(FocusEvent.AddTask)
        runCurrent()
        vm.onEvent(FocusEvent.Start)
        runCurrent()

        passSeconds(5 * 60)

        val state = vm.state.value
        assertEquals(PerchPhase.LANDING, state.phase)
        assertEquals(20, state.landingAutoStartSeconds)
        val session = repository.snapshot.value.sessions.single()
        assertEquals(SessionOutcome.COMPLETED, session.outcome)
        assertEquals(5 * 60, session.elapsedSeconds)
        assertEquals(1, state.completedBlocksToday)
        assertEquals(5, state.focusMinutesToday)

        // Auto-start after 20 s of nobody deciding.
        passSeconds(20)
        assertEquals(PerchPhase.RESTING, vm.state.value.phase)
        assertEquals(5 * 60, vm.state.value.remainingSeconds)
        assertEquals(2, repository.snapshot.value.sessions.size)
    }

    @Test
    fun `land and hop marks the perched task done and perches the next one without stopping the clock`() = runPerchTest {
        val vm = viewModel()
        listOf("First", "Second").forEach {
            vm.onEvent(FocusEvent.DraftChanged(it))
            vm.onEvent(FocusEvent.AddTask)
        }
        runCurrent()
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(10)

        vm.onEvent(FocusEvent.LandPerched)
        runCurrent()

        val state = vm.state.value
        assertEquals(PerchPhase.FOCUSING, state.phase)
        assertEquals("Second", state.perched?.title)
        assertEquals(1, state.landedTasksToday)
        assertEquals(25 * 60 - 10, state.remainingSeconds)

        vm.onEvent(FocusEvent.UndoLastLanding)
        runCurrent()
        assertEquals("First", vm.state.value.perched?.title)
        assertEquals(0, vm.state.value.landedTasksToday)
    }

    @Test
    fun `finish early asks first then logs a stopped-early session`() = runPerchTest {
        val vm = viewModel()
        vm.onEvent(FocusEvent.DraftChanged("Task A"))
        vm.onEvent(FocusEvent.AddTask)
        runCurrent()
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(3 * 60)

        vm.onEvent(FocusEvent.FinishEarlyRequested)
        runCurrent()
        val dialog = vm.state.value.dialog
        assertTrue(dialog is PerchDialog.FinishEarly)
        assertEquals(3, (dialog as PerchDialog.FinishEarly).minutesLogged)
        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)

        vm.onEvent(FocusEvent.FinishEarlyConfirmed)
        runCurrent()
        assertEquals(PerchPhase.ROOSTING, vm.state.value.phase)
        assertNull(vm.state.value.dialog)
        val session = repository.snapshot.value.sessions.single()
        assertEquals(SessionOutcome.STOPPED_EARLY, session.outcome)
        assertEquals(180, session.elapsedSeconds)
    }

    @Test
    fun `pause freezes the clock and resume continues`() = runPerchTest {
        val vm = viewModel()
        vm.onEvent(FocusEvent.DraftChanged("Task A"))
        vm.onEvent(FocusEvent.AddTask)
        runCurrent()
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(30)

        vm.onEvent(FocusEvent.Pause)
        runCurrent()
        passSeconds(60)
        assertEquals(PerchPhase.PAUSED, vm.state.value.phase)
        assertEquals(25 * 60 - 30, vm.state.value.remainingSeconds)
        assertEquals(60, vm.state.value.pausedForSeconds)

        vm.onEvent(FocusEvent.Resume)
        runCurrent()
        passSeconds(10)
        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)
        assertEquals(25 * 60 - 40, vm.state.value.remainingSeconds)
    }

    @Test
    fun `skip break starts the next block when a task is waiting`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        listOf("First", "Second").forEach {
            vm.onEvent(FocusEvent.DraftChanged(it))
            vm.onEvent(FocusEvent.AddTask)
        }
        runCurrent()
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(5 * 60)
        assertEquals(PerchPhase.LANDING, vm.state.value.phase)

        vm.onEvent(FocusEvent.LandPerched)
        runCurrent()
        assertTrue(vm.state.value.perchedLandedDuringBreak)
        assertEquals("Second", vm.state.value.perched?.title)

        vm.onEvent(FocusEvent.SkipBreak)
        runCurrent()
        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)
        assertEquals("Second", vm.state.value.perched?.title)
        assertFalse(vm.state.value.perchedLandedDuringBreak)
        assertEquals(2, repository.snapshot.value.sessions.count { it.kind == SessionKind.FOCUS })
    }

    @Test
    fun `queue actions perch next move and delete with confirmation`() = runPerchTest {
        val vm = viewModel()
        listOf("A", "B", "C").forEach {
            vm.onEvent(FocusEvent.DraftChanged(it))
            vm.onEvent(FocusEvent.AddTask)
        }
        runCurrent()
        assertEquals(listOf("B", "C"), vm.state.value.queue.map { it.title })

        vm.onEvent(FocusEvent.PerchNext("id-2"))
        runCurrent()
        assertEquals("C", vm.state.value.perched?.title)
        assertEquals(listOf("A", "B"), vm.state.value.queue.map { it.title })

        vm.onEvent(FocusEvent.MoveTask("id-1", -1))
        runCurrent()
        assertEquals(listOf("B", "A"), vm.state.value.queue.map { it.title })

        vm.onEvent(FocusEvent.DeleteTaskRequested("id-1"))
        runCurrent()
        assertNotNull(vm.state.value.dialog)
        assertEquals(3, repository.snapshot.value.tasks.size)
        vm.onEvent(FocusEvent.DeleteTaskConfirmed)
        runCurrent()
        assertEquals(2, repository.snapshot.value.tasks.size)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `orphan running sessions from a previous process are closed on init`() = runPerchTest {
        repository = FakePerchRepository(
            PerchSnapshot(
                tasks = listOf(Task("t", "Old", now - 3_600_000L, 0)),
                sessions = listOf(
                    io.embodit.perch.domain.model.FocusSession(
                        id = "orphan", kind = SessionKind.FOCUS, taskId = "t", taskTitle = "Old",
                        startedAtMillis = now - 10 * 60_000L, plannedSeconds = 25 * 60,
                    ),
                ),
            ),
        )
        val vm = viewModel()
        runCurrent()
        val session = repository.snapshot.value.sessions.single()
        assertEquals(SessionOutcome.STOPPED_EARLY, session.outcome)
        assertEquals(10 * 60, session.elapsedSeconds)
        assertEquals(10, vm.state.value.focusMinutesToday)
        assertEquals(PerchPhase.ROOSTING, vm.state.value.phase)
    }

    @Test
    fun `break reaching zero emits BreakEnded and closes the break session`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        val effects = mutableListOf<FocusEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }
        vm.onEvent(FocusEvent.DraftChanged("Task A"))
        vm.onEvent(FocusEvent.AddTask)
        runCurrent()
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(5 * 60)
        vm.onEvent(FocusEvent.StartBreak)
        runCurrent()
        assertEquals(PerchPhase.RESTING, vm.state.value.phase)

        passSeconds(5 * 60)

        assertEquals(PerchPhase.ROOSTING, vm.state.value.phase)
        assertTrue(effects.contains(FocusEffect.BlockEnded))
        assertTrue(effects.contains(FocusEffect.BreakEnded))
        val breakSession = repository.snapshot.value.sessions.single { it.kind == SessionKind.BREAK }
        assertEquals(SessionOutcome.COMPLETED, breakSession.outcome)
        assertEquals(5 * 60, breakSession.elapsedSeconds)
    }

    @Test
    fun `a double start creates exactly one session`() = runPerchTest {
        val vm = viewModel()
        vm.onEvent(FocusEvent.DraftChanged("Task A"))
        vm.onEvent(FocusEvent.AddTask)
        runCurrent()

        vm.onEvent(FocusEvent.Start)
        vm.onEvent(FocusEvent.Start)
        runCurrent()

        assertEquals(1, repository.snapshot.value.sessions.size)
        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)
    }

    @Test
    fun `a persisted running block resumes after a process restart`() = runPerchTest {
        val live = FocusSession(
            id = "live", kind = SessionKind.FOCUS, taskId = "t", taskTitle = "Keep going",
            startedAtMillis = now - 10 * 60_000L, plannedSeconds = 25 * 60,
        )
        repository = FakePerchRepository(
            PerchSnapshot(tasks = listOf(Task("t", "Keep going", now - 3_600_000L, 0)), sessions = listOf(live)),
        )
        store = FocusSessionStore(
            TimerState(
                phase = TimerPhase.FOCUS, sessionId = "live", taskId = "t",
                plannedSeconds = 25 * 60, endsAtMillis = now + 15 * 60_000L,
            ),
        )
        val vm = viewModel()
        runCurrent()
        passSeconds(1)

        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)
        assertEquals(15 * 60 - 1, vm.state.value.remainingSeconds)
        assertEquals(SessionOutcome.RUNNING, repository.snapshot.value.sessions.single().outcome)
        assertEquals(10, vm.state.value.focusMinutesToday)
        assertEquals("Keep going", vm.state.value.blockTaskTitle)
    }

    @Test
    fun `a block that expired while away lands without auto-starting the break`() = runPerchTest {
        val live = FocusSession(
            id = "live", kind = SessionKind.FOCUS, taskId = "t", taskTitle = "Old block",
            startedAtMillis = now - 26 * 60_000L, plannedSeconds = 25 * 60,
        )
        repository = FakePerchRepository(
            PerchSnapshot(tasks = listOf(Task("t", "Old block", now - 3_600_000L, 0)), sessions = listOf(live)),
        )
        store = FocusSessionStore(
            TimerState(
                phase = TimerPhase.FOCUS, sessionId = "live", taskId = "t",
                plannedSeconds = 25 * 60, endsAtMillis = now - 60_000L,
            ),
        )
        val vm = viewModel()
        runCurrent()
        passSeconds(1)

        assertEquals(PerchPhase.LANDING, vm.state.value.phase)
        assertEquals(0, vm.state.value.landingAutoStartSeconds)
        assertEquals(SessionOutcome.COMPLETED, repository.snapshot.value.sessions.single().outcome)

        passSeconds(40)
        assertEquals(PerchPhase.LANDING, vm.state.value.phase)
    }

    @Test
    fun `reconciliation waits until the repository has loaded`() = runPerchTest {
        val orphan = FocusSession(
            id = "orphan", kind = SessionKind.FOCUS, taskId = "t", taskTitle = "Old",
            startedAtMillis = now - 10 * 60_000L, plannedSeconds = 25 * 60,
        )
        repository = FakePerchRepository(
            PerchSnapshot(tasks = listOf(Task("t", "Old", now - 3_600_000L, 0)), sessions = listOf(orphan)),
            loaded = false,
        )
        viewModel()
        runCurrent()
        assertEquals(SessionOutcome.RUNNING, repository.snapshot.value.sessions.single().outcome)

        repository.markLoaded()
        runCurrent()
        assertEquals(SessionOutcome.STOPPED_EARLY, repository.snapshot.value.sessions.single().outcome)
    }

    @Test
    fun `undo of a named landing restores that task even after another landing`() = runPerchTest {
        val vm = viewModel()
        listOf("A", "B", "C").forEach {
            vm.onEvent(FocusEvent.DraftChanged(it))
            vm.onEvent(FocusEvent.AddTask)
            runCurrent()
        }
        vm.onEvent(FocusEvent.LandTask("id-0"))
        runCurrent()
        vm.onEvent(FocusEvent.LandTask("id-1"))
        runCurrent()
        assertEquals("C", vm.state.value.perched?.title)

        vm.onEvent(FocusEvent.UndoLanding("id-0"))
        runCurrent()
        assertEquals("A", vm.state.value.perched?.title)
        assertEquals(1, vm.state.value.landedTasksToday)
    }

    @Test
    fun `the queue face lists every open task and switch perches the next one`() = runPerchTest {
        val vm = viewModel()
        listOf("A", "B", "C").forEach {
            vm.onEvent(FocusEvent.DraftChanged(it))
            vm.onEvent(FocusEvent.AddTask)
            runCurrent()
        }
        vm.onEvent(FocusEvent.OpenQueue)
        runCurrent()
        assertTrue(vm.state.value.showLog)
        assertEquals(LogTab.QUEUE, vm.state.value.logTab)
        assertEquals(listOf("A", "B", "C"), vm.state.value.openTasks.map { it.title })

        vm.onEvent(FocusEvent.SwitchToNext)
        runCurrent()
        assertEquals("B", vm.state.value.perched?.title)
    }

    private fun TestScope.addTasks(vm: FocusViewModel, vararg titles: String) {
        titles.forEach {
            vm.onEvent(FocusEvent.DraftChanged(it))
            vm.onEvent(FocusEvent.AddTask)
            runCurrent()
        }
    }

    @Test
    fun `pausing in the last instant ends the block properly instead of losing it`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        val effects = mutableListOf<FocusEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }
        addTasks(vm, "Task A")
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(5 * 60 - 1)
        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)

        // The wall clock passes the end between two ticks and the user hits Pause right then.
        now += 1200L
        vm.onEvent(FocusEvent.Pause)
        runCurrent()

        assertEquals(PerchPhase.LANDING, vm.state.value.phase)
        val session = repository.snapshot.value.sessions.single()
        assertEquals(SessionOutcome.COMPLETED, session.outcome)
        assertEquals(5 * 60, session.elapsedSeconds)
        assertTrue(effects.contains(FocusEffect.BlockEnded))
    }

    @Test
    fun `a restored block shows the right clock before the first second passes`() = runPerchTest {
        val live = FocusSession(
            id = "live", kind = SessionKind.FOCUS, taskId = "t", taskTitle = "Keep going",
            startedAtMillis = now - 10 * 60_000L, plannedSeconds = 25 * 60,
        )
        repository = FakePerchRepository(
            PerchSnapshot(tasks = listOf(Task("t", "Keep going", now - 3_600_000L, 0)), sessions = listOf(live)),
        )
        // What timer.json holds: the end time, no countdown.
        store = FocusSessionStore(
            TimerState(
                phase = TimerPhase.FOCUS, sessionId = "live", taskId = "t",
                plannedSeconds = 25 * 60, endsAtMillis = now + 15 * 60_000L, remainingSeconds = 0,
            ),
        )
        val vm = viewModel()
        runCurrent()

        assertEquals(15 * 60, vm.state.value.remainingSeconds)
        assertEquals(10, vm.state.value.focusMinutesToday)
        assertFalse(vm.state.value.canExtend)
    }

    @Test
    fun `a live block whose session record is missing gets one back`() = runPerchTest {
        repository = FakePerchRepository(PerchSnapshot(tasks = listOf(Task("t", "Keep going", now - 3_600_000L, 0))))
        store = FocusSessionStore(
            TimerState(
                phase = TimerPhase.FOCUS, sessionId = "live", taskId = "t",
                plannedSeconds = 25 * 60, endsAtMillis = now + 15 * 60_000L,
            ),
        )
        val vm = viewModel()
        runCurrent()

        val rebuilt = repository.snapshot.value.sessions.single()
        assertEquals("live", rebuilt.id)
        assertEquals("Keep going", rebuilt.taskTitle)
        assertEquals(now - 10 * 60_000L, rebuilt.startedAtMillis)

        passSeconds(15 * 60)
        assertEquals(PerchPhase.LANDING, vm.state.value.phase)
        assertEquals(SessionOutcome.COMPLETED, repository.snapshot.value.sessions.single().outcome)
    }

    @Test
    fun `the break chip undoes the task it landed even after another landing`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        addTasks(vm, "A", "B", "C")
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(5 * 60)
        assertEquals(PerchPhase.LANDING, vm.state.value.phase)

        vm.onEvent(FocusEvent.LandPerched) // the chip lands A
        runCurrent()
        assertEquals("id-0", vm.state.value.breakLandedTaskId)
        vm.onEvent(FocusEvent.LandTask("id-2")) // a queue checkbox lands C
        runCurrent()
        assertEquals("id-0", vm.state.value.breakLandedTaskId)

        vm.onEvent(FocusEvent.UndoLanding("id-0")) // what the toggled chip sends
        runCurrent()
        assertNull(vm.state.value.breakLandedTaskId)
        assertEquals("A", vm.state.value.perched?.title)
        assertTrue(repository.snapshot.value.tasks.single { it.id == "id-2" }.isDone)
    }

    @Test
    fun `the finish dialog follows the clock and goes away when the block ends by itself`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        addTasks(vm, "Task A")
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(60)
        vm.onEvent(FocusEvent.FinishEarlyRequested)
        runCurrent()
        assertEquals(1, (vm.state.value.dialog as PerchDialog.FinishEarly).minutesLogged)

        passSeconds(120)
        assertEquals(3, (vm.state.value.dialog as PerchDialog.FinishEarly).minutesLogged)

        passSeconds(120)
        assertEquals(PerchPhase.LANDING, vm.state.value.phase)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `switching mid-block re-points the block and the up arrow only lines a task up`() = runPerchTest {
        val vm = viewModel()
        addTasks(vm, "A", "B", "C")
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(120)

        val effects = mutableListOf<FocusEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }
        vm.onEvent(FocusEvent.PerchNext("id-2")) // C lines up behind A, the block stays on A
        runCurrent()
        assertTrue(effects.contains(FocusEffect.NextUp("C")))
        assertEquals("A", vm.state.value.perched?.title)
        assertEquals(listOf("C", "B"), vm.state.value.queue.map { it.title })
        assertEquals("A", repository.snapshot.value.sessions.single().taskTitle)

        vm.onEvent(FocusEvent.SwitchToNext) // now the block is about C
        runCurrent()
        assertEquals("C", vm.state.value.perched?.title)
        assertEquals("C", vm.state.value.blockTaskTitle)
        val session = repository.snapshot.value.sessions.single()
        assertEquals("id-2", session.taskId)
        assertEquals(SessionOutcome.RUNNING, session.outcome)
        assertEquals(25 * 60 - 120, vm.state.value.remainingSeconds)
    }

    @Test
    fun `the day wire axis stretches to cover blocks outside the work hours`() = runPerchTest {
        val early = FocusSession(
            id = "early", kind = SessionKind.FOCUS, taskId = null, taskTitle = "Early bird",
            startedAtMillis = now - 3 * 3_600_000L - 20 * 60_000L, plannedSeconds = 25 * 60, // 06:40 UTC
            elapsedSeconds = 25 * 60, endedAtMillis = now - 3 * 3_600_000L + 5 * 60_000L,
            outcome = SessionOutcome.COMPLETED,
        )
        repository = FakePerchRepository(PerchSnapshot(sessions = listOf(early)))
        val vm = viewModel()
        runCurrent()

        assertEquals(6 * 60, vm.state.value.dayStartMinute)
        assertEquals(18 * 60, vm.state.value.dayEndMinute)
        assertEquals(6 * 60 + 40, vm.state.value.wireBlocks.single().startMinuteOfDay)
    }

    @Test
    fun `during a break the up arrow picks the next block once the finished task is landed`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        addTasks(vm, "A", "B", "C", "D")
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(5 * 60)
        assertEquals(PerchPhase.LANDING, vm.state.value.phase)

        vm.onEvent(FocusEvent.PerchNext("id-3")) // A still perched: D lines up behind it
        runCurrent()
        assertEquals(listOf("A", "D", "B", "C"), vm.state.value.openTasks.map { it.title })

        vm.onEvent(FocusEvent.LandTask("id-0")) // A landed by its checkbox counts like the chip
        runCurrent()
        assertEquals("id-0", vm.state.value.breakLandedTaskId)
        vm.onEvent(FocusEvent.PerchNext("id-2")) // now the arrow chooses the next block
        runCurrent()
        assertEquals("C", vm.state.value.perched?.title)

        vm.onEvent(FocusEvent.SkipBreak)
        runCurrent()
        assertEquals(PerchPhase.FOCUSING, vm.state.value.phase)
        assertEquals("C", repository.snapshot.value.sessions.last { it.kind == SessionKind.FOCUS }.taskTitle)
    }

    @Test
    fun `a break cut off within seconds stays out of the log and the wire`() = runPerchTest {
        repository.updateSettings(FocusSettings(focusMinutes = 5, breakMinutes = 5))
        val vm = viewModel()
        addTasks(vm, "Task A")
        vm.onEvent(FocusEvent.Start)
        runCurrent()
        passSeconds(5 * 60)
        vm.onEvent(FocusEvent.StartBreak)
        runCurrent()
        passSeconds(5)
        vm.onEvent(FocusEvent.BackEarly)
        runCurrent()
        vm.onEvent(FocusEvent.OpenLog)
        runCurrent()

        assertEquals(PerchPhase.ROOSTING, vm.state.value.phase)
        assertEquals(listOf(SessionKind.FOCUS), vm.state.value.logRows.map { it.kind })
        assertEquals(listOf(SessionKind.FOCUS), vm.state.value.wireBlocks.map { it.kind })
    }
}
