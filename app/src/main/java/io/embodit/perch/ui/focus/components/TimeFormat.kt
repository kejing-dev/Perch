package io.embodit.perch.ui.focus.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.embodit.perch.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** mm:ss, or h:mm:ss above one hour. */
fun formatClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}

/** "1 h 45 m" / "45 m" / "0 m", in the app language. */
@Composable
fun minutesText(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val rest = m % 60
    return when {
        h > 0 && rest > 0 -> stringResource(R.string.duration_h_m, h, rest)
        h > 0 -> stringResource(R.string.duration_h, h)
        else -> stringResource(R.string.duration_m, rest)
    }
}

/** Local short time, e.g. "09:40" or "9:40 AM" depending on locale. */
fun formatTimeOfDay(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** Local short date, e.g. "Sep 25". */
fun formatShortDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

/** Day-wire hour label: "9", "12", "1" on a 12-hour clock; "9", "13", "0" on a 24-hour clock. */
fun hourLabel(hourOfDay: Int, is24Hour: Boolean): String {
    val hour = ((hourOfDay % 24) + 24) % 24
    return if (is24Hour) hour.toString() else (if (hour % 12 == 0) 12 else hour % 12).toString()
}
