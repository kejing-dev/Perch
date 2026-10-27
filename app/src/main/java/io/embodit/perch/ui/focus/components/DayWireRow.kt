package io.embodit.perch.ui.focus.components

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.LinearProgressDefaults
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import io.embodit.perch.R
import io.embodit.perch.domain.model.SessionKind
import io.embodit.perch.domain.model.SessionOutcome
import io.embodit.perch.ui.focus.FocusUiState
import io.embodit.perch.ui.theme.PerchColors
import io.embodit.perch.ui.theme.fixed

/**
 * The day wire: today's blocks laid on a work-hours time axis, the tally and the daily goal bar.
 * The whole 60 dp row opens the log.
 */
@Composable
fun DayWireRow(state: FocusUiState, onOpenLog: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .spatialHoverEffect()
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onOpenLog)
            .controllerHapticFeedback(interactionSource = interaction)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DayWireTrack(state = state, modifier = Modifier.weight(1f).height(44.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val resources = LocalContext.current.resources
            val tally =
                if (state.completedBlocksToday == 0 && state.focusMinutesToday == 0 && state.landedTasksToday == 0) {
                    stringResource(R.string.tally_empty)
                } else {
                    resources.getQuantityString(R.plurals.tally_blocks, state.completedBlocksToday, state.completedBlocksToday) +
                        " · " + stringResource(R.string.tally_rest, minutesText(state.focusMinutesToday), state.landedTasksToday)
                }
            Text(text = tally, style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { state.goalProgress },
                    modifier = Modifier.width(96.dp),
                    colors = LinearProgressDefaults.linearProgressColors(
                        indicatorColor = PicoTheme.colorScheme.passable,
                        backgroundColor = PicoTheme.colorScheme.fillTertiary,
                    ),
                    height = LinearProgressDefaults.linearProgressHeight(4.dp),
                )
                Text(
                    text = stringResource(R.string.goal_of, minutesText(state.goalMinutes)),
                    style = PicoTheme.typography.labelMedium,
                    color = PicoTheme.colorScheme.labelTertiary,
                    maxLines = 1,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = stringResource(R.string.log),
            modifier = Modifier.size(24.dp),
            tint = PicoTheme.colorScheme.labelSecondary,
        )
    }
}

/** Duration as length: one rounded bar per block at its clock position, a dot for now, hour labels below. */
@Composable
private fun DayWireTrack(state: FocusUiState, modifier: Modifier = Modifier) {
    val moss = PerchColors.Moss.fixed()
    val ochre = PerchColors.Ochre.fixed()
    val robin = PerchColors.Robin.fixed()
    val graphite = PerchColors.Graphite.fixed()
    val track = PerchColors.WireTrack.fixed()
    val measurer = rememberTextMeasurer()
    val labelStyle = PicoTheme.typography.labelSmall.copy(color = PicoTheme.colorScheme.labelTertiary)
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    Canvas(modifier = modifier) {
        val start = state.dayStartMinute
        val span = (state.dayEndMinute - start).coerceAtLeast(60).toFloat()
        val pxPerMinute = size.width / span
        val midY = 14.dp.toPx()
        val barHeight = 14.dp.toPx()
        val minWidth = 6.dp.toPx()
        // Label every hour when there is room, otherwise every second or third one.
        val pxPerHour = pxPerMinute * 60f
        val labelStep = when {
            pxPerHour >= 36.dp.toPx() -> 1
            pxPerHour >= 18.dp.toPx() -> 2
            else -> 3
        }

        drawLine(color = track, start = Offset(0f, midY), end = Offset(size.width, midY), strokeWidth = 2.dp.toPx())
        var hourMinute = ((start + 59) / 60) * 60
        while (hourMinute <= state.dayEndMinute) {
            val x = (hourMinute - start) * pxPerMinute
            drawLine(color = track, start = Offset(x, midY - 5.dp.toPx()), end = Offset(x, midY + 5.dp.toPx()), strokeWidth = 1.dp.toPx())
            val hour = hourMinute / 60
            if (hour % labelStep == 0) {
                val layout = measurer.measure(text = hourLabel(hour, is24Hour), style = labelStyle, maxLines = 1)
                val labelX = (x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                drawText(textLayoutResult = layout, topLeft = Offset(labelX, midY + 10.dp.toPx()))
            }
            hourMinute += 60
        }

        state.wireBlocks.forEach { block ->
            val x = ((block.startMinuteOfDay - start) * pxPerMinute).coerceIn(0f, size.width)
            val width = (block.minutes * pxPerMinute).coerceAtLeast(minWidth).coerceAtMost(size.width - x)
            val color = when {
                block.kind == SessionKind.BREAK -> robin
                block.outcome == SessionOutcome.RUNNING -> ochre
                else -> moss
            }
            val height = if (block.kind == SessionKind.BREAK) barHeight * 0.5f else barHeight
            val topLeft = Offset(x, midY - height / 2f)
            val barSize = Size(width, height)
            if (block.outcome == SessionOutcome.STOPPED_EARLY && block.kind == SessionKind.FOCUS) {
                drawRoundRect(color = color, topLeft = topLeft, size = barSize, cornerRadius = CornerRadius(height / 2f), style = Stroke(width = 2.dp.toPx()))
            } else {
                drawRoundRect(color = color, topLeft = topLeft, size = barSize, cornerRadius = CornerRadius(height / 2f))
            }
        }

        val nowX = ((state.nowMinuteOfDay - start) * pxPerMinute).coerceIn(0f, size.width)
        drawCircle(color = graphite, radius = 5.dp.toPx(), center = Offset(nowX, midY))
    }
}
