package io.embodit.perch.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Typography
import io.embodit.perch.R

/**
 * Noto Sans (SIL Open Font License 1.1, see third_party/noto-sans/OFL.txt).
 *
 * Perch keeps the PicoTheme type scale (sizes, weights, line heights, tracking) untouched and only
 * swaps the typeface, so every SpatialUI component keeps its tuned rhythm while the app renders in
 * Noto Sans. Weight mapping: Medium (body), SemiBold (title/label), Bold (display/headline).
 */
val NotoSans: FontFamily =
    FontFamily(
        Font(R.font.noto_sans_regular, FontWeight.Normal),
        Font(R.font.noto_sans_medium, FontWeight.Medium),
        Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
        Font(R.font.noto_sans_bold, FontWeight.Bold),
    )

private fun TextStyle.inNotoSans(): TextStyle = copy(fontFamily = NotoSans)

/** Returns [base] with every role re-pointed at Noto Sans. */
fun Typography.withNotoSans(): Typography =
    copy(
        bodyLarge = bodyLarge.inNotoSans(),
        bodyLargeMultiline = bodyLargeMultiline.inNotoSans(),
        bodyMedium = bodyMedium.inNotoSans(),
        bodyMediumMultiline = bodyMediumMultiline.inNotoSans(),
        bodySmall = bodySmall.inNotoSans(),
        bodyTiny = bodyTiny.inNotoSans(),
        displayLarge = displayLarge.inNotoSans(),
        displayMedium = displayMedium.inNotoSans(),
        displaySmall = displaySmall.inNotoSans(),
        headlineLarge = headlineLarge.inNotoSans(),
        headlineMedium = headlineMedium.inNotoSans(),
        headlineSmall = headlineSmall.inNotoSans(),
        labelLarge = labelLarge.inNotoSans(),
        labelMedium = labelMedium.inNotoSans(),
        labelSmall = labelSmall.inNotoSans(),
        titleLarge = titleLarge.inNotoSans(),
        titleMedium = titleMedium.inNotoSans(),
        titleSmall = titleSmall.inNotoSans(),
    )
