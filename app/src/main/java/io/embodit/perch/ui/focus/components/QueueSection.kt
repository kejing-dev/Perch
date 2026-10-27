package io.embodit.perch.ui.focus.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.ButtonChip
import com.pico.spatial.ui.design.Checkbox
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.ListItem
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import io.embodit.perch.R
import io.embodit.perch.ui.focus.FocusEvent
import io.embodit.perch.ui.focus.TaskUi

/** A queue row: 48 dp controls inside 8 dp vertical padding (the ListItem default of 16 dp makes 80 dp rows). */
internal val QueueRowHeight = 64.dp
internal val QueueRowGap = 4.dp
private val QueueRowPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)

/**
 * The queue on bare glass: the next tasks after the perched one, receded while a block runs.
 * Rows beyond [maxRows] are reachable through the "+n more" chip, which opens the full queue.
 */
@Composable
fun QueueSection(
    queue: List<TaskUi>,
    receded: Boolean,
    maxRows: Int,
    onEvent: (FocusEvent) -> Unit,
    modifier: Modifier = Modifier,
    onRowHeightChanged: (Int) -> Unit = {},
) {
    val visible = queue.take(maxRows.coerceAtLeast(1))
    val overflow = queue.size - visible.size
    Column(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (receded) 0.7f else 1f }.then(modifier),
        verticalArrangement = Arrangement.spacedBy(QueueRowGap),
    ) {
        visible.forEachIndexed { index, task ->
            QueueRow(
                task = task,
                modifier = if (index == 0) Modifier.onSizeChanged { onRowHeightChanged(it.height) } else Modifier,
                overflow = if (index == visible.lastIndex) overflow else 0,
                canMoveUp = index > 0,
                canMoveDown = index < queue.lastIndex,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun QueueRow(
    task: TaskUi,
    overflow: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEvent: (FocusEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        padding = QueueRowPadding,
        headlineContent = {
            Text(
                text = task.title,
                style = PicoTheme.typography.bodyLarge,
                color = PicoTheme.colorScheme.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Checkbox(checked = false, onCheckedChange = { onEvent(FocusEvent.LandTask(task.id)) })
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (overflow > 0) {
                    ButtonChip(
                        label = {
                            // design-style: inherited-content-color ButtonChip
                            Text(text = stringResource(R.string.queue_more, overflow), style = PicoTheme.typography.labelMedium)
                        },
                        onClick = { onEvent(FocusEvent.OpenQueue) },
                    )
                }
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
                RowMenuButton(task = task, canMoveUp = canMoveUp, canMoveDown = canMoveDown, onEvent = onEvent)
            }
        },
    )
}

@Composable
private fun RowMenuButton(task: TaskUi, canMoveUp: Boolean, canMoveDown: Boolean, onEvent: (FocusEvent) -> Unit) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { showMenu = true },
            size = IconButtonDefaults.iconButtonSize(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = PicoTheme.colorScheme.fillTertiary,
                contentColor = PicoTheme.colorScheme.labelPrimary,
            ),
        ) {
            Icon(painter = painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.cd_task_menu), modifier = Modifier.size(20.dp))
        }
        if (showMenu) {
            Menu(onDismissRequest = { showMenu = false }) {
                if (canMoveUp) {
                    MenuItem(
                        title = {
                            // design-style: inherited-content-color MenuItem
                            Text(text = stringResource(R.string.menu_move_up))
                        },
                        onClick = {
                            showMenu = false
                            onEvent(FocusEvent.MoveTask(task.id, -1))
                        },
                        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_chevron_up), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    )
                }
                if (canMoveDown) {
                    MenuItem(
                        title = {
                            // design-style: inherited-content-color MenuItem
                            Text(text = stringResource(R.string.menu_move_down))
                        },
                        onClick = {
                            showMenu = false
                            onEvent(FocusEvent.MoveTask(task.id, 1))
                        },
                        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_chevron_down), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    )
                }
                MenuItem(
                    title = {
                        // design-style: inherited-content-color MenuItem
                        Text(text = stringResource(R.string.menu_delete))
                    },
                    onClick = {
                        showMenu = false
                        onEvent(FocusEvent.DeleteTaskRequested(task.id))
                    },
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.error) },
                )
            }
        }
    }
}
