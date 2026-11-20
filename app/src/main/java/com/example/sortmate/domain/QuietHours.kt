package com.example.sortmate.domain

import java.time.LocalTime

/**
 * Quiet hours can cross midnight, e.g. 22:00 -> 06:00.
 * Times are stored as minutes from midnight so they are easy to persist.
 */
object QuietHours {

    fun isActive(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val now = normalize(nowMinutes)
        val start = normalize(startMinutes)
        val end = normalize(endMinutes)
        if (start == end) return false
        return if (start < end) {
            now in start until end
        } else {
            now >= start || now < end
        }
    }

    fun isActive(now: LocalTime, startMinutes: Int, endMinutes: Int): Boolean =
        isActive(now.hour * 60 + now.minute, startMinutes, endMinutes)

    fun format(minutes: Int): String {
        val normalized = normalize(minutes)
        val hour = normalized / 60
        val minute = normalized % 60
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour = when (hour % 12) {
            0 -> 12
            else -> hour % 12
        }
        return "%d:%02d %s".format(displayHour, minute, suffix)
    }

    private fun normalize(minutes: Int): Int = ((minutes % 1440) + 1440) % 1440
}
