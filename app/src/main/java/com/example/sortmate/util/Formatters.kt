package com.example.sortmate.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

fun formatPeso(cents: Long): String =
    "₱" + String.format(Locale.US, "%,.2f", cents / 100.0)

fun formatPesoShort(cents: Long): String {
    val pesos = cents / 100.0
    return if (pesos >= 1_000) {
        String.format(Locale.US, "₱%.1fk", pesos / 1_000)
    } else {
        "₱" + String.format(Locale.US, "%,.2f", pesos)
    }
}

fun formatDueDate(epochMillis: Long?): String {
    if (epochMillis == null) return "No due date"
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date.isBefore(today) -> "Overdue · ${dateFormatter.format(date)}"
        date == today -> "Due today"
        date == today.plusDays(1) -> "Due tomorrow"
        else -> "Due ${dateFormatter.format(date)}"
    }
}

fun daysFromNow(days: Long): Long =
    LocalDate.now().plusDays(days)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

fun initials(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

fun normalizedJoinCode(value: String): String =
    value.uppercase(Locale.US).filter(Char::isLetterOrDigit).take(6)
