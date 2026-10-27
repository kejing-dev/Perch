package io.embodit.perch.ui.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.design.Divider
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.CoachmarkBox
import com.pico.spatial.ui.design.windows.CoachmarkDefaults
import com.pico.spatial.ui.design.windows.CoachmarkDirection
import com.pico.spatial.ui.design.windows.LocalSnackbarHostState
import com.pico.spatial.ui.design.windows.SimpleCoachmark
import com.pico.spatial.ui.design.windows.SnackbarResult
import io.embodit.perch.R
import io.embodit.perch.platform.PerchGraph
import io.embodit.perch.ui.focus.components.Chime
import io.embodit.perch.ui.focus.components.DayWireRow
import io.embodit.perch.ui.focus.components.LogSheet
import io.embodit.perch.ui.focus.components.PerchCard
import io.embodit.perch.ui.focus.components.PerchDialogs
import io.embodit.perch.ui.focus.components.QueueRowGap
import io.embodit.perch.ui.focus.components.QueueRowHeight
import io.embodit.perch.ui.focus.components.QueueSection
import io.embodit.perch.ui.focus.components.QuickAddRow
import kotlinx.coroutines.launch

/** Stateful shell: wires the ViewModel, state and one-shot effects, then renders [FocusContent]. */
@Composable
fun FocusScreen(modifier: Modifier = Modifier, factory: ViewModelProvider.Factory = defaultFactory()) {
    val viewModel: FocusViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        // Each effect is handled in its own coroutine: a snackbar that waits for dismissal must
        // not delay the chime of the block that ends behind it.
        viewModel.effects.collect { effect ->
            scope.launch {
                when (effect) {
                    is FocusEffect.Landed -> {
                        val result = snackbar.show(
                            message = context.getString(R.string.landed_snack, effect.title),
                            action = context.getString(R.string.undo),
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.onEvent(FocusEvent.UndoLanding(effect.taskId))
                    }
                    is FocusEffect.Logged -> snackbar.show(message = context.getString(R.string.logged_snack, effect.minutes))
                    is FocusEffect.NextUp -> snackbar.show(message = context.getString(R.string.next_up_snack, effect.title))
                    FocusEffect.BlockEnded, FocusEffect.BreakEnded -> Chime.play()
                    FocusEffect.LoadFailed -> snackbar.show(message = context.getString(R.string.load_failed))
                }
            }
        }
    }

    FocusContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun defaultFactory(): ViewModelProvider.Factory {
    val context = LocalContext.current
    return remember(context) {
        val graph = PerchGraph.from(context)
        FocusViewModel.Factory(graph.repository, graph.sessionStore, graph.externalEvents)
    }
}

/** Stateless layout: card → queue → quick-add → divider → day wire, plus overlays. */
@Composable
internal fun FocusContent(state: FocusUiState, onEvent: (FocusEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            CoachmarkBox(
                showCoachmark = state.showCoachmark,
                direction = CoachmarkDirection.Above,
                coachmark = {
                    SimpleCoachmark(
                        text = {
                            Text(
                                text = stringResource(R.string.coachmark_text),
                                style = PicoTheme.typography.bodyMedium,
                                color = PicoTheme.colorScheme.labelPrimary,
                            )
                        },
                        button = {
                            CoachmarkDefaults.CoachmarkButton(onClick = { onEvent(FocusEvent.DismissCoachmark) }) {
                                // design-style: inherited-content-color CoachmarkButton
                                Text(text = stringResource(R.string.got_it), style = PicoTheme.typography.labelMedium)
                            }
                        },
                    )
                },
            ) {
                PerchCard(state = state, onEvent = onEvent)
            }
            // The card and the day wire keep their height; the queue takes what is left between them.
            if (state.phase != PerchPhase.EMPTY) {
                QueueArea(state = state, onEvent = onEvent, modifier = Modifier.weight(1f))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            DayWireRow(state = state, onOpenLog = { onEvent(FocusEvent.OpenLog) })
        }
        PerchDialogs(dialog = state.dialog, onEvent = onEvent)
        LogSheet(state = state, onEvent = onEvent)
    }
}

/** Queue rows that fit the space left under the card, then the quick-add row right below them. */
@Composable
private fun QueueArea(state: FocusUiState, onEvent: (FocusEvent) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().clipToBounds()) {
        val density = LocalDensity.current
        var rowHeightPx by remember { mutableIntStateOf(0) }
        var quickAddHeightPx by remember { mutableIntStateOf(0) }
        val rowHeight = if (rowHeightPx > 0) with(density) { rowHeightPx.toDp() } else QueueRowHeight
        val quickAddHeight = if (quickAddHeightPx > 0) with(density) { quickAddHeightPx.toDp() } else 56.dp
        val forRows = maxHeight - QueueTopGap - QuickAddGap - quickAddHeight
        // n rows take n * rowHeight + (n - 1) * QueueRowGap.
        val queueRows = ((forRows + QueueRowGap) / (rowHeight + QueueRowGap)).toInt().coerceIn(1, 4)
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(QueueTopGap))
            QueueSection(
                queue = state.queue,
                receded = state.isBlockActive || state.isBreakPhase,
                maxRows = queueRows,
                onEvent = onEvent,
                onRowHeightChanged = { rowHeightPx = it },
            )
            Spacer(modifier = Modifier.height(QuickAddGap))
            QuickAddRow(draft = state.draft, onEvent = onEvent, modifier = Modifier.onSizeChanged { quickAddHeightPx = it.height })
        }
    }
}

private val QueueTopGap = 8.dp
private val QuickAddGap = 4.dp
