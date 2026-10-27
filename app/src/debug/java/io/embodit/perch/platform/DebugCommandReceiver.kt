package io.embodit.perch.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.embodit.perch.ui.focus.FocusEvent
import io.embodit.perch.ui.focus.LogTab

/**
 * Debug builds only. Drives the focus state machine from adb so every state can be screenshotted
 * on the emulator without spatial input:
 *
 *   adb shell am broadcast -a io.embodit.perch.DEBUG --es cmd add --es title "Write the brief"
 *   adb shell am broadcast -a io.embodit.perch.DEBUG --es cmd start
 *   adb shell am broadcast -a io.embodit.perch.DEBUG --es cmd advance --ei seconds 600
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = PerchGraph.from(context)
        val events: List<FocusEvent> = when (intent.getStringExtra("cmd")) {
            "add" -> intent.getStringExtra("title")?.let { listOf(FocusEvent.DraftChanged(it), FocusEvent.AddTask) }.orEmpty()
            "draft" -> listOf(FocusEvent.DraftChanged(intent.getStringExtra("title").orEmpty()))
            "start" -> listOf(FocusEvent.Start)
            "pause" -> listOf(FocusEvent.Pause)
            "resume" -> listOf(FocusEvent.Resume)
            "land" -> listOf(FocusEvent.LandPerched)
            "undo" -> listOf(FocusEvent.UndoLastLanding)
            "extend" -> listOf(FocusEvent.Extend(intent.getIntExtra("seconds", 600)))
            "finish" -> listOf(FocusEvent.FinishEarlyRequested)
            "finish_confirm" -> listOf(FocusEvent.FinishEarlyConfirmed)
            "break" -> listOf(FocusEvent.StartBreak)
            "skip" -> listOf(FocusEvent.SkipBreak)
            "back" -> listOf(FocusEvent.BackEarly)
            "perch" -> intent.getStringExtra("id")?.let { listOf(FocusEvent.PerchNext(it)) }.orEmpty()
            "landtask" -> intent.getStringExtra("id")?.let { listOf(FocusEvent.LandTask(it)) }.orEmpty()
            "move" -> intent.getStringExtra("id")?.let { listOf(FocusEvent.MoveTask(it, intent.getIntExtra("delta", 1))) }.orEmpty()
            "delete" -> intent.getStringExtra("id")?.let { listOf(FocusEvent.DeleteTaskRequested(it)) }.orEmpty()
            "delete_confirm" -> listOf(FocusEvent.DeleteTaskConfirmed)
            "dismiss" -> listOf(FocusEvent.DismissDialog)
            "minutes" -> listOf(FocusEvent.SelectFocusMinutes(intent.getIntExtra("minutes", 25)))
            "durations" -> listOf(FocusEvent.ShowDurations(intent.getBooleanExtra("show", true)))
            "log" -> listOf(FocusEvent.OpenLog)
            "queue" -> listOf(FocusEvent.OpenQueue)
            "switch" -> listOf(FocusEvent.SwitchToNext)
            "closelog" -> listOf(FocusEvent.CloseLog)
            "logtab" -> listOf(FocusEvent.SelectLogTab(LogTab.valueOf(intent.getStringExtra("tab") ?: "TODAY")))
            "coachmark_done" -> listOf(FocusEvent.DismissCoachmark)
            "advance" -> listOf(FocusEvent.AdvanceClockForTesting(intent.getIntExtra("seconds", 60)))
            else -> emptyList()
        }
        events.forEach { graph.externalEvents.tryEmit(it) }
    }
}
