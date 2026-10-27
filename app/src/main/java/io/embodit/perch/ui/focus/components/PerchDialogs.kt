package io.embodit.perch.ui.focus.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog
import io.embodit.perch.R
import io.embodit.perch.ui.focus.FocusEvent
import io.embodit.perch.ui.focus.PerchDialog

/** The two confirmations Perch asks for: ending a block early and deleting a task. */
@Composable
fun PerchDialogs(dialog: PerchDialog?, onEvent: (FocusEvent) -> Unit) {
    when (dialog) {
        is PerchDialog.FinishEarly ->
            ConfirmDialog(
                title = stringResource(R.string.finish_early_title),
                // Names the task the block is logged for, which may already have been landed.
                body = if (dialog.taskTitle.isBlank()) {
                    stringResource(R.string.finish_early_body_no_task, dialog.minutesLogged)
                } else {
                    stringResource(R.string.finish_early_body, dialog.minutesLogged, dialog.taskTitle)
                },
                confirmLabel = stringResource(R.string.finish_early_confirm),
                onConfirm = { onEvent(FocusEvent.FinishEarlyConfirmed) },
                onDismiss = { onEvent(FocusEvent.DismissDialog) },
            )
        is PerchDialog.DeleteTask ->
            ConfirmDialog(
                title = stringResource(R.string.delete_task_title),
                body = stringResource(R.string.delete_task_body, dialog.task.title),
                confirmLabel = stringResource(R.string.delete),
                onConfirm = { onEvent(FocusEvent.DeleteTaskConfirmed) },
                onDismiss = { onEvent(FocusEvent.DismissDialog) },
            )
        null -> Unit
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = PicoTheme.typography.headlineMedium, color = PicoTheme.colorScheme.labelPrimary) },
        content = { Text(text = body, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary) },
        buttons = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End)) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillSecondary,
                        contentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                ) {
                    // design-style: inherited-content-color Button
                    Text(text = stringResource(R.string.cancel), style = PicoTheme.typography.labelLarge)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.error,
                        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
                ) {
                    // design-style: inherited-content-color Button
                    Text(text = confirmLabel, style = PicoTheme.typography.labelLarge)
                }
            }
        },
    )
}
