package com.example.sortmate.domain

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {

    @Test
    fun `overnight range is active after start`() {
        assertTrue(QuietHours.isActive(minutes(23, 0), minutes(22, 0), minutes(6, 0)))
    }

    @Test
    fun `overnight range is active before end`() {
        assertTrue(QuietHours.isActive(minutes(2, 30), minutes(22, 0), minutes(6, 0)))
    }

    @Test
    fun `overnight range is inactive during the day`() {
        assertFalse(QuietHours.isActive(minutes(12, 0), minutes(22, 0), minutes(6, 0)))
        assertFalse(QuietHours.isActive(minutes(21, 59), minutes(22, 0), minutes(6, 0)))
        assertFalse(QuietHours.isActive(minutes(6, 0), minutes(22, 0), minutes(6, 0)))
    }

    @Test
    fun `daytime range is respected`() {
        assertTrue(QuietHours.isActive(minutes(9, 0), minutes(9, 0), minutes(17, 0)))
        assertTrue(QuietHours.isActive(minutes(16, 59), minutes(9, 0), minutes(17, 0)))
        assertFalse(QuietHours.isActive(minutes(17, 0), minutes(9, 0), minutes(17, 0)))
    }

    @Test
    fun `equal start and end means no quiet hours`() {
        assertFalse(QuietHours.isActive(minutes(22, 0), minutes(22, 0), minutes(22, 0)))
    }

    @Test
    fun `local time overload matches minutes overload`() {
        assertTrue(
            QuietHours.isActive(LocalTime.of(23, 30), minutes(22, 0), minutes(6, 0)),
        )
        assertFalse(
            QuietHours.isActive(LocalTime.NOON, minutes(22, 0), minutes(6, 0)),
        )
    }

    @Test
    fun `format handles midnight noon and pm`() {
        assertEquals("12:00 AM", QuietHours.format(0))
        assertEquals("12:00 PM", QuietHours.format(12 * 60))
        assertEquals("10:05 PM", QuietHours.format(22 * 60 + 5))
        assertEquals("6:00 AM", QuietHours.format(6 * 60))
    }

    private fun minutes(hour: Int, minute: Int): Int = hour * 60 + minute
}
