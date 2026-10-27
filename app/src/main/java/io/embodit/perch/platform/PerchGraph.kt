package io.embodit.perch.platform

import android.content.Context
import io.embodit.perch.data.local.JsonFileTimerStore
import io.embodit.perch.data.repository.JsonFilePerchRepository
import io.embodit.perch.data.repository.PerchRepository
import io.embodit.perch.data.session.FocusSessionStore
import io.embodit.perch.domain.model.TimerPhase
import io.embodit.perch.domain.usecase.FocusTimerUseCase
import io.embodit.perch.ui.focus.FocusEvent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tiny hand-rolled object graph: one repository backed by `files/perch.json`, one process-wide
 * timer store mirrored to `files/timer.json`, and a bus for events raised outside the window
 * (debug adb commands today; a keyboard-shortcut or widget entry point tomorrow). Created by
 * [SpatialApplication]; the screen's default ViewModel factory reads it from the application context.
 *
 * Load order matters: the saved timer is restored *before* the repository reports itself loaded, so
 * the ViewModel's orphan-session reconciliation never mistakes the live block for a dead one.
 */
class PerchGraph(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fileRepository = JsonFilePerchRepository(File(context.filesDir, "perch.json"))
    private val timerFile = JsonFileTimerStore(File(context.filesDir, "timer.json"))

    val repository: PerchRepository = fileRepository
    val sessionStore: FocusSessionStore = FocusSessionStore()
    val externalEvents = MutableSharedFlow<FocusEvent>(extraBufferCapacity = 32)

    init {
        appScope.launch {
            timerFile.load()?.let { saved ->
                // The file never holds the per-second countdown; recompute it before anything renders.
                val restored = FocusTimerUseCase().refresh(saved, System.currentTimeMillis())
                sessionStore.update { current -> if (current.phase == TimerPhase.IDLE) restored else current }
            }
            fileRepository.load()
            sessionStore.timer
                .map(JsonFileTimerStore::projection)
                .distinctUntilChanged()
                .drop(1)
                .collect { timerFile.save(it) }
        }
    }

    companion object {
        fun from(context: Context): PerchGraph =
            (context.applicationContext as SpatialApplication).graph
    }
}
