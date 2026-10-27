package io.embodit.perch.ui.focus.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Checkbox
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.ListItem
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ScrollIndicator
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Sheet
import io.embodit.perch.R
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.ui.focus.FocusEvent
import io.embodit.perch.ui.focus.FocusUiState
import io.embodit.perch.ui.focus.LogRowUi
import io.embodit.perch.ui.focus.LogTab

private val SheetContentWidth = 328.dp
private val SheetMaxContentHeight = 420.dp
private val SheetMinContentHeight = 240.dp
private val LogTabs = listOf(LogTab.TODAY, LogTab.YESTERDAY, LogTab.WEEK)

/**
 * One modal sheet with two faces: the log (today / yesterday / 7 days of blocks) and the full
 * queue (every open task, reached from the "+n more" chip).
 *
 * The sheet window sizes itself from its content, so the content keeps one fixed size for as long
 * as the sheet is open: a resize after showing left the surface black on the 6.1 emulator. The
 * height follows the main window (the sheet cannot be taller than its host).
 */
@Composable
fun LogSheet(state: FocusUiState, onEvent: (FocusEvent) -> Unit) {
    if (!state.showLog) return
    val hostHeight = LocalConfiguration.current.screenHeightDp.dp
    // Pinned for as long as the sheet is open (this remember starts over each time it opens).
    val contentHeight = remember { (hostHeight - 120.dp).coerceIn(SheetMinContentHeight, SheetMaxContentHeight) }
    val showQueue = state.logTab == LogTab.QUEUE
    Sheet(
        onDismissRequest = { onEvent(FocusEvent.CloseLog) },
        title = {
            Text(
                text = stringResource(if (showQueue) R.string.queue_title else R.string.log_title),
                style = PicoTheme.typography.titleMedium,
                color = PicoTheme.colorScheme.labelPrimary,
            )
        },
    ) {
        Column(
            modifier = Modifier.width(SheetContentWidth).height(contentHeight).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showQueue) {
                QueueFace(state = state, onEvent = onEvent, listWidth = SheetContentWidth - 32.dp)
            } else {
                LogFace(state = state, onEvent = onEvent, listWidth = SheetContentWidth - 32.dp)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LogFace(state: FocusUiState, onEvent: (FocusEvent) -> Unit, listWidth: Dp) {
    SegmentControl(modifier = Modifier.width(listWidth)) {
        LogTabs.forEach { tab ->
            SegmentItem(
                selected = state.logTab == tab,
                onClick = { onEvent(FocusEvent.SelectLogTab(tab)) },
                modifier = Modifier.weight(1f),
                title = {
                    // design-style: inherited-content-color SegmentItem
                    Text(
                        text = stringResource(
                            when (tab) {
                                LogTab.YESTERDAY -> R.string.log_yesterday
                                LogTab.WEEK -> R.string.log_week
                                else -> R.string.log_today
                            },
                        ),
                        style = PicoTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
    Text(
        text = stringResource(R.string.log_total, minutesText(state.logFocusMinutes)),
        style = PicoTheme.typography.headlineMedium,
        color = PicoTheme.colorScheme.labelPrimary,
    )
    if (state.logRows.isEmpty()) {
        Text(text = stringResource(R.string.log_empty), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
    } else {
        Box(modifier = Modifier.weight(1f)) {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.width(listWidth).fillMaxHeight()) {
                items(state.logRows, key = { it.id }) { row -> LogRow(row = row, showDate = state.logTab == LogTab.WEEK) }
            }
            ScrollIndicator(state = listState)
        }
    }
}

@Composable
private fun LogRow(row: LogRowUi, showDate: Boolean) {
    val isBreak = row.kind == SessionKind.BREAK
    val time = formatTimeOfDay(row.startedAtMillis)
    val dateLabel = if (showDate) "${formatShortDate(row.startedAtMillis)} · $time" else time
    ListItem(
        headlineContent = {
            Text(
                text = if (isBreak) stringResource(R.string.log_break_title) else row.title,
                style = PicoTheme.typography.bodyLarge,
                color = PicoTheme.colorScheme.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            val detail = stringResource(R.string.log_row_focus, row.minutes, dateLabel)
            val suffix = if (row.outcome == SessionOutcome.STOPPED_EARLY) " · ${stringResource(R.string.log_stopped_early)}" else ""
            Text(text = detail + suffix, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelSecondary)
        },
        leadingContent = {
            Icon(
                painter = painterResource(if (isBreak) R.drawable.ic_cafe else if (row.outcome == SessionOutcome.COMPLETED) R.drawable.ic_check_circle else R.drawable.ic_circle),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isBreak) PicoTheme.colorScheme.alert else PicoTheme.colorScheme.passable,
            )
        },
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.QueueFace(state: FocusUiState, onEvent: (FocusEvent) -> Unit, listWidth: Dp) {
    if (state.openTasks.isEmpty()) {
        Text(text = stringResource(R.string.queue_empty), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        return
    }
    Box(modifier = Modifier.weight(1f)) {
        val listState = rememberLazyListState()
        LazyColumn(state = listState, modifier = Modifier.width(listWidth).fillMaxHeight()) {
            items(state.openTasks, key = { it.id }) { task ->
                val isPerched = task.id == state.perched?.id
                ListItem(
                    headlineContent = {
                        Text(
                            text = task.title,
                            style = PicoTheme.typography.bodyLarge,
                            color = PicoTheme.colorScheme.labelPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = if (isPerched) {
                        {
                            Text(
                                text = stringResource(R.string.queue_perched),
                                style = PicoTheme.typography.bodySmall,
                                color = PicoTheme.colorScheme.interaction,
                            )
                        }
                    } else {
                        null
                    },
                    leadingContent = {
                        Checkbox(checked = false, onCheckedChange = { onEvent(FocusEvent.LandTask(task.id)) })
                    },
                    trailingContent = {
                        if (!isPerched) {
                            IconButton(
                                onClick = { onEvent(FocusEvent.PerchNext(task.id)) },
                                size = IconButtonDefaults.iconButtonSize(48.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = PicoTheme.colorScheme.fillTertiary,
                                    contentColor = PicoTheme.colorScheme.labelPrimary,
                                ),
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_arrow_up), contentDescription = stringResource(R.string.cd_perch_next), modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                )
            }
        }
        ScrollIndicator(state = listState)
    }
}
