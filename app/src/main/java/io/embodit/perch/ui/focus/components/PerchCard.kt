package io.embodit.perch.ui.focus.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonChip
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.LinearProgressDefaults
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.ToggleableChip
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.foundation.layout.zOffset
import io.embodit.perch.R
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.ui.focus.FocusEvent
import io.embodit.perch.ui.focus.FocusUiState
import io.embodit.perch.ui.focus.PerchPhase
import io.embodit.perch.ui.theme.PerchColors
import io.embodit.perch.ui.theme.fixed

private val CardShape = RoundedCornerShape(24.dp)
private val ControlSize = 56.dp

/**
 * The Perch card: the one lifted surface in the window. Opaque paper over the glass, +16 dp
 * (+24 dp while a break is due), holding the clock, the perched task, the controls and the branch.
 */
@Composable
fun PerchCard(state: FocusUiState, onEvent: (FocusEvent) -> Unit, modifier: Modifier = Modifier) {
    val lift by animateDpAsState(
        targetValue = if (state.phase == PerchPhase.LANDING) 24.dp else 16.dp,
        animationSpec = tween(durationMillis = 300),
        label = "perchLift",
    )
    val paper = if (state.isBreakPhase) PerchColors.PaperBlush else PerchColors.Paper
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zOffset { lift.toPx() }
            .clip(CardShape)
            .background(paper.fixed())
            .border(1.dp, PerchColors.PaperEdge.fixed(), CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state.phase) {
            PerchPhase.EMPTY -> EmptyContent(state, onEvent)
            PerchPhase.ROOSTING -> RoostingContent(state, onEvent)
            PerchPhase.FOCUSING, PerchPhase.PAUSED -> FocusingContent(state, onEvent)
            PerchPhase.LANDING, PerchPhase.RESTING -> BreakContent(state, onEvent)
        }
    }
}

@Composable
private fun EmptyContent(state: FocusUiState, onEvent: (FocusEvent) -> Unit) {
    Text(
        text = stringResource(R.string.empty_prompt),
        style = PicoTheme.typography.headlineMedium,
        color = PerchColors.Graphite.fixed(),
    )
    QuickAddRow(draft = state.draft, onEvent = onEvent, onPaper = true)
    Text(
        text = stringResource(if (state.loadError) R.string.load_failed else R.string.empty_hint),
        style = PicoTheme.typography.bodyMedium,
        color = PerchColors.GraphiteSoft.fixed(),
    )
    Branch(progress = 0f, color = PicoTheme.colorScheme.interaction, track = PerchColors.OchreTrack.fixed())
}

@Composable
private fun RoostingContent(state: FocusUiState, onEvent: (FocusEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.perch_next_label),
                style = PicoTheme.typography.labelMedium,
                color = PerchColors.GraphiteSoft.fixed(),
            )
            Text(
                text = state.perched?.title ?: stringResource(R.string.nothing_perched),
                style = PicoTheme.typography.headlineMedium,
                color = PerchColors.Graphite.fixed(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SegmentControl(modifier = Modifier.width(216.dp)) {
            FocusSettings.FOCUS_PRESETS_MINUTES.forEach { minutes ->
                SegmentItem(
                    selected = state.selectedFocusMinutes == minutes,
                    onClick = { onEvent(FocusEvent.SelectFocusMinutes(minutes)) },
                    modifier = Modifier.weight(1f),
                    title = {
                        // design-style: inherited-content-color SegmentItem
                        Text(text = minutes.toString(), style = PicoTheme.typography.labelLarge)
                    },
                )
            }
        }
        DurationsAnchor(state = state, onEvent = onEvent)
    }
    Button(
        onClick = { onEvent(FocusEvent.Start) },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.perched != null,
        colors = ButtonDefaults.buttonColors(
            containerColor = PicoTheme.colorScheme.interaction,
            contentColor = PicoTheme.colorScheme.labelPrimaryLight,
        ),
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(20.dp))
        },
    ) {
        // design-style: inherited-content-color Button
        Text(text = stringResource(R.string.start_block, state.selectedFocusMinutes), style = PicoTheme.typography.labelLarge)
    }
    Branch(progress = 0f, color = PicoTheme.colorScheme.interaction, track = PerchColors.OchreTrack.fixed())
}

@Composable
private fun FocusingContent(state: FocusUiState, onEvent: (FocusEvent) -> Unit) {
    val paused = state.phase == PerchPhase.PAUSED
    val ink = PerchColors.Graphite.fixed()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = formatClock(state.remainingSeconds),
            style = PicoTheme.typography.displayLarge,
            color = if (paused) PerchColors.GraphiteSoft.fixed() else ink,
            maxLines = 1,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.perched?.title ?: stringResource(R.string.nothing_perched),
                style = PicoTheme.typography.headlineMedium,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateChip(paused = paused, onEvent = onEvent)
                val meta =
                    if (paused) {
                        stringResource(R.string.paused_for, minutesText(state.pausedForSeconds / 60))
                    } else {
                        val began = state.blockStartedAtMillis?.let(::formatTimeOfDay)
                        if (began != null) {
                            stringResource(R.string.meta_focus, state.plannedSeconds / 60, state.blockIndexInCycle, state.blocksPerCycle, began)
                        } else {
                            stringResource(R.string.meta_focus_short, state.plannedSeconds / 60, state.blockIndexInCycle, state.blocksPerCycle)
                        }
                    }
                // The meta text yields width first so the chips beside it always keep their size.
                Text(
                    text = meta,
                    modifier = Modifier.weight(1f, fill = false),
                    style = PicoTheme.typography.labelMedium,
                    color = PerchColors.GraphiteSoft.fixed(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.canExtend) {
                    ButtonChip(
                        label = {
                            // design-style: inherited-content-color ButtonChip
                            Text(text = stringResource(R.string.extend_ten), style = PicoTheme.typography.labelMedium)
                        },
                        onClick = { onEvent(FocusEvent.Extend(10 * 60)) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PaperIconButton(
                icon = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
                contentDescription = stringResource(if (paused) R.string.cd_resume else R.string.cd_pause),
                onClick = { onEvent(if (paused) FocusEvent.Resume else FocusEvent.Pause) },
            )
            PaperIconButton(
                icon = R.drawable.ic_check,
                contentDescription = stringResource(R.string.cd_land),
                onClick = { onEvent(FocusEvent.LandPerched) },
                enabled = state.perched != null,
            )
            MoreMenuButton(state = state, onEvent = onEvent)
        }
    }
    Branch(
        progress = state.progress,
        color = if (paused) PerchColors.GraphiteSoft.fixed() else PicoTheme.colorScheme.interaction,
        track = PerchColors.OchreTrack.fixed(),
    )
}

/** Focusing / Paused, encoded by glyph + label + color; tapping it toggles pause. */
@Composable
private fun StateChip(paused: Boolean, onEvent: (FocusEvent) -> Unit) {
    ButtonChip(
        label = {
            // design-style: inherited-content-color ButtonChip
            Text(text = stringResource(if (paused) R.string.paused else R.string.focusing), style = PicoTheme.typography.labelMedium)
        },
        onClick = { onEvent(if (paused) FocusEvent.Resume else FocusEvent.Pause) },
        leadingIcon = {
            Icon(
                painter = painterResource(if (paused) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        },
        colors = ChipsDefaults.chipColors(
            if (paused) PerchColors.Graphite.fixed() else PicoTheme.colorScheme.interaction,
            if (paused) PerchColors.WireTrack.fixed() else PerchColors.OchreTrack.fixed(),
        ),
    )
}

@Composable
private fun BreakContent(state: FocusUiState, onEvent: (FocusEvent) -> Unit) {
    val landing = state.phase == PerchPhase.LANDING
    val ink = PerchColors.Graphite.fixed()
    val breakMinutes = if (state.nextBreakIsLong) state.settings.longBreakMinutes else state.settings.breakMinutes
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_eye),
            contentDescription = stringResource(R.string.cd_eye),
            modifier = Modifier.size(40.dp),
            tint = PicoTheme.colorScheme.alert,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(if (landing) R.string.eyes_off else if (state.nextBreakIsLong) R.string.long_break else R.string.resting),
                style = PicoTheme.typography.headlineMedium,
                color = ink,
            )
            Text(
                text = stringResource(R.string.look_far),
                style = PicoTheme.typography.bodyMedium,
                color = PerchColors.GraphiteSoft.fixed(),
            )
        }
        Text(
            text = formatClock(if (landing) breakMinutes * 60 else state.remainingSeconds),
            style = PicoTheme.typography.displayLarge,
            color = PicoTheme.colorScheme.alert,
            maxLines = 1,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // The chip takes whatever the buttons leave, so a long task title is not cut at half the row.
        Box(modifier = Modifier.weight(1f)) {
            val perched = state.perched
            if (perched != null || state.perchedLandedDuringBreak) {
                ToggleableChip(
                    label = {
                        // design-style: inherited-content-color ToggleableChip
                        Text(
                            text = if (state.perchedLandedDuringBreak) stringResource(R.string.landed_chip) else stringResource(R.string.land_task_chip, perched?.title.orEmpty()),
                            style = PicoTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    isToggleOn = state.perchedLandedDuringBreak,
                    onClick = { onEvent(state.breakLandedTaskId?.let { FocusEvent.UndoLanding(it) } ?: FocusEvent.LandPerched) },
                )
            }
        }
        if (landing) {
            Button(
                onClick = { onEvent(FocusEvent.StartBreak) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.alert,
                    contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                ),
            ) {
                // design-style: inherited-content-color Button
                Text(
                    text = if (state.landingAutoStartSeconds > 0) stringResource(R.string.start_break_auto, state.landingAutoStartSeconds) else stringResource(R.string.start_break),
                    style = PicoTheme.typography.labelLarge,
                )
            }
            Button(
                onClick = { onEvent(FocusEvent.SkipBreak) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PerchColors.RobinTrack.fixed(),
                    contentColor = ink,
                ),
            ) {
                // design-style: inherited-content-color Button
                Text(text = stringResource(if (state.perched != null) R.string.skip_to_next else R.string.skip_break), style = PicoTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = { onEvent(FocusEvent.BackEarly) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PerchColors.RobinTrack.fixed(),
                    contentColor = ink,
                ),
            ) {
                // design-style: inherited-content-color Button
                Text(text = stringResource(R.string.back_early), style = PicoTheme.typography.labelLarge)
            }
        }
    }
    Branch(
        progress = if (landing) 1f else 1f - state.progress,
        color = PicoTheme.colorScheme.alert,
        track = PerchColors.RobinTrack.fixed(),
    )
}

/** The 12 dp branch: fills while focusing, drains while resting, empty outline while roosting. */
@Composable
private fun Branch(progress: Float, color: Color, track: Color) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
        colors = LinearProgressDefaults.linearProgressColors(indicatorColor = color, backgroundColor = track),
        height = LinearProgressDefaults.linearProgressHeight(12.dp),
    )
}

@Composable
private fun PaperIconButton(icon: Int, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        size = IconButtonDefaults.iconButtonSize(ControlSize),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = PerchColors.OchreTrack.fixed(),
            contentColor = PerchColors.Graphite.fixed(),
        ),
    ) {
        Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun MoreMenuButton(state: FocusUiState, onEvent: (FocusEvent) -> Unit) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    Box {
        PaperIconButton(
            icon = R.drawable.ic_more,
            contentDescription = stringResource(R.string.cd_more),
            onClick = { showMenu = true },
        )
        if (state.showDurations) {
            DurationsPopup(settings = state.settings, onEvent = onEvent)
        }
        if (showMenu) {
            Menu(onDismissRequest = { showMenu = false }) {
                MenuItem(
                    title = {
                        // design-style: inherited-content-color MenuItem
                        Text(text = stringResource(R.string.menu_extend))
                    },
                    onClick = {
                        showMenu = false
                        onEvent(FocusEvent.Extend(10 * 60))
                    },
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
                if (state.queue.isNotEmpty()) {
                    MenuItem(
                        title = {
                            // design-style: inherited-content-color MenuItem
                            Text(text = stringResource(R.string.menu_switch))
                        },
                        onClick = {
                            showMenu = false
                            onEvent(FocusEvent.SwitchToNext)
                        },
                        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_skip), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    )
                }
                MenuItem(
                    title = {
                        // design-style: inherited-content-color MenuItem
                        Text(text = stringResource(R.string.menu_finish_early))
                    },
                    onClick = {
                        showMenu = false
                        onEvent(FocusEvent.FinishEarlyRequested)
                    },
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_stop), contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
                MenuItem(
                    title = {
                        // design-style: inherited-content-color MenuItem
                        Text(text = stringResource(R.string.menu_durations))
                    },
                    onClick = {
                        showMenu = false
                        onEvent(FocusEvent.ShowDurations(true))
                    },
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_tune), contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
            }
        }
    }
}

@Composable
private fun DurationsAnchor(state: FocusUiState, onEvent: (FocusEvent) -> Unit) {
    Box {
        PaperIconButton(
            icon = R.drawable.ic_tune,
            contentDescription = stringResource(R.string.cd_durations),
            onClick = { onEvent(FocusEvent.ShowDurations(true)) },
        )
        if (state.showDurations) {
            DurationsPopup(settings = state.settings, onEvent = onEvent)
        }
    }
}
