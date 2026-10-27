package io.embodit.perch.ui.focus.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `clock shows minutes and seconds and adds hours above one hour`() {
        assertEquals("00:00", formatClock(-5))
        assertEquals("24:59", formatClock(24 * 60 + 59))
        assertEquals("1:30:00", formatClock(90 * 60))
    }

    @Test
    fun `hour labels follow the 12 or 24 hour clock`() {
        assertEquals(listOf("9", "12", "1", "12"), listOf(9, 12, 13, 24).map { hourLabel(it, is24Hour = false) })
        assertEquals(listOf("9", "12", "13", "0"), listOf(9, 12, 13, 24).map { hourLabel(it, is24Hour = true) })
    }
}
