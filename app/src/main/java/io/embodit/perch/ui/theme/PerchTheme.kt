package io.embodit.perch.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * Perch's PicoTheme: the system Vibrant scheme with the design's four semantic colors swapped in,
 * plus the full PicoTheme type scale rendered in Noto Sans. Every one of the 16 roles is assigned
 * explicitly so the final theme contract is visible here rather than implied by defaults.
 */
@Composable
fun PerchTheme(content: @Composable () -> Unit) {
    val system = systemColorScheme(LocalContext.current)
    val scheme =
        system.copy(
            fillPrimary = system.fillPrimary,
            fillSecondary = system.fillSecondary,
            fillTertiary = system.fillTertiary,
            fillLight = system.fillLight,
            labelPrimaryLight = system.labelPrimaryLight,
            labelPrimary = system.labelPrimary,
            labelSecondary = system.labelSecondary,
            labelTertiary = system.labelTertiary,
            labelQuaternary = system.labelQuaternary,
            lightenHover = system.lightenHover,
            lightenPressed = system.lightenPressed,
            error = PerchColors.Error,
            alert = PerchColors.Robin,
            passable = PerchColors.Moss,
            interaction = PerchColors.Ochre,
            dividerLine = system.dividerLine,
        )
    PicoTheme(
        colorScheme = scheme,
        typography = PicoTheme.typography.withNotoSans(),
        content = content,
    )
}
