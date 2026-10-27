package io.embodit.perch.ui.focus.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.NumberField
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.SpatialPopup
import com.pico.spatial.ui.design.windows.popup.HorizontalPlacement
import com.pico.spatial.ui.design.windows.popup.VerticalPlacement
import com.pico.spatial.ui.design.windows.rememberSpatialPopupPositionProvider
import io.embodit.perch.R
import io.embodit.perch.domain.model.FocusSettings
import io.embodit.perch.ui.focus.FocusEvent

/** Anchored popup with the four numbers the user may want to tune. Place it inside the anchor's Box. */
@Composable
fun DurationsPopup(settings: FocusSettings, onEvent: (FocusEvent) -> Unit) {
    SpatialPopup(
        onDismissRequest = { onEvent(FocusEvent.ShowDurations(false)) },
        popupPositionProvider = rememberSpatialPopupPositionProvider(
            horizontalPlacement = HorizontalPlacement.alignEnd(),
            verticalPlacement = VerticalPlacement.below(offset = 8.dp),
        ),
        defaultMinWidth = 380.dp,
        defaultMinHeight = Dp.Unspecified,
    ) {
        Column(
            modifier = Modifier.width(380.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.durations_title),
                style = PicoTheme.typography.titleMedium,
                color = PicoTheme.colorScheme.labelPrimary,
            )
            SettingRow(label = stringResource(R.string.focus_minutes)) {
                NumberField(
                    value = settings.focusMinutes,
                    onValueChange = { onEvent(FocusEvent.UpdateSettings(settings.copy(focusMinutes = it))) },
                    stepLength = 5,
                    valueRange = FocusSettings.MIN_FOCUS_MINUTES..FocusSettings.MAX_FOCUS_MINUTES,
                )
            }
            SettingRow(label = stringResource(R.string.break_minutes)) {
                NumberField(
                    value = settings.breakMinutes,
                    onValueChange = { onEvent(FocusEvent.UpdateSettings(settings.copy(breakMinutes = it))) },
                    stepLength = 1,
                    valueRange = FocusSettings.MIN_BREAK_MINUTES..FocusSettings.MAX_BREAK_MINUTES,
                )
            }
            SettingRow(label = stringResource(R.string.long_break_minutes)) {
                NumberField(
                    value = settings.longBreakMinutes,
                    onValueChange = { onEvent(FocusEvent.UpdateSettings(settings.copy(longBreakMinutes = it))) },
                    stepLength = 5,
                    valueRange = FocusSettings.MIN_BREAK_MINUTES..FocusSettings.MAX_BREAK_MINUTES,
                )
            }
            SettingRow(label = stringResource(R.string.goal_minutes)) {
                NumberField(
                    value = settings.goalMinutes,
                    onValueChange = { onEvent(FocusEvent.UpdateSettings(settings.copy(goalMinutes = it))) },
                    stepLength = 30,
                    valueRange = FocusSettings.MIN_GOAL_MINUTES..FocusSettings.MAX_GOAL_MINUTES,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onEvent(FocusEvent.ShowDurations(false)) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.interaction,
                        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
                ) {
                    // design-style: inherited-content-color Button
                    Text(text = stringResource(R.string.done), style = PicoTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, field: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        field()
    }
}
