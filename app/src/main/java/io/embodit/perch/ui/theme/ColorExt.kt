package io.embodit.perch.ui.theme

import androidx.compose.ui.graphics.Color
import com.pico.spatial.ui.foundation.vibrant.Vibrant
import com.pico.spatial.ui.foundation.vibrant.withVibrant

/**
 * Ink and paper colors on the opaque Perch card must not be re-tinted by the Vibrant system that
 * adapts colors to the glass behind them; [fixed] pins a design token to its literal value.
 */
fun Color.fixed(): Color = withVibrant(Vibrant.None)
