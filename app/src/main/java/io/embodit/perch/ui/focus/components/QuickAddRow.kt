package io.embodit.perch.ui.focus.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextField
import io.embodit.perch.R
import io.embodit.perch.ui.focus.FocusEvent
import io.embodit.perch.ui.theme.PerchColors
import io.embodit.perch.ui.theme.fixed

/** Type a line, press Enter. [onPaper] adapts the placeholder ink when the row sits inside the Perch card. */
@Composable
fun QuickAddRow(draft: String, onEvent: (FocusEvent) -> Unit, modifier: Modifier = Modifier, onPaper: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = draft,
            onValueChange = { onEvent(FocusEvent.DraftChanged(it)) },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(R.string.add_task_placeholder),
                    style = PicoTheme.typography.bodyLarge,
                    color = if (onPaper) PerchColors.GraphiteSoft.fixed() else PicoTheme.colorScheme.labelTertiary,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(FocusEvent.AddTask) }),
        )
        Button(
            onClick = { onEvent(FocusEvent.AddTask) },
            enabled = draft.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.interaction,
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        ) {
            // design-style: inherited-content-color Button
            Text(text = stringResource(R.string.add), style = PicoTheme.typography.labelLarge)
        }
    }
}
