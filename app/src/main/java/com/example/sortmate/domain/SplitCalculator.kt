package com.example.sortmate.domain

/**
 * Pure Kotlin split logic so it can be unit-tested without Android.
 *
 * Splits a total evenly across participants and distributes the leftover cent(s)
 * to the first participants, guaranteeing the shares always add up to the total.
 */
object SplitCalculator {

    fun evenShares(totalCents: Long, participantCount: Int): List<Long> {
        if (totalCents <= 0L || participantCount <= 0) return emptyList()
        val base = totalCents / participantCount
        val remainder = (totalCents % participantCount).toInt()
        return List(participantCount) { index ->
            if (index < remainder) base + 1 else base
        }
    }

    fun shareFor(totalCents: Long, participantCount: Int): Long {
        if (participantCount <= 0) return 0L
        return evenShares(totalCents, participantCount).firstOrNull() ?: 0L
    }
}
