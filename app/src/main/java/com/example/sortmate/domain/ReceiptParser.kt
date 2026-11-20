package com.example.sortmate.domain

/**
 * Regex-based parser for ML Kit output. Kept free of Android imports so the
 * receipt cases can be covered by fast JVM unit tests.
 *
 * Heuristics:
 *  - Prefer lines containing "grand total", "total due", or "amount due".
 *  - Never pick a line containing "subtotal", "vat", "tax", or "discount".
 *  - Fall back to the largest currency-looking amount on the receipt.
 */
object ReceiptParser {

    private val amountRegex = Regex(
        """(?i)(?:php|p|₱|\$)?\s*(\d{1,3}(?:[,.]\d{3})*(?:\.\d{2})|\d+\.\d{2})""",
    )
    private val dateRegex = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""")
    private val ignoredTotalWords = listOf("subtotal", "sub-total", "vat", "tax", "discount", "change", "cash")
    private val strongTotalWords = listOf("grand total", "total due", "amount due", "total amount")
    private val weakTotalWords = listOf("total")

    fun parse(rawText: String): ReceiptScanResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val merchant = lines.firstOrNull { line ->
            line.any(Char::isLetter) && amountRegex.find(line) == null && line.length <= 48
        }

        val strongCandidates = mutableListOf<Pair<Long, String>>()
        val weakCandidates = mutableListOf<Pair<Long, String>>()
        val allAmounts = mutableListOf<Pair<Long, String>>()

        lines.forEach { line ->
            val lower = line.lowercase()
            val amount = amountRegex.find(line)?.groupValues?.getOrNull(1)
                ?.let(::parseAmountCents)
                ?: return@forEach

            allAmounts += amount to line
            when {
                ignoredTotalWords.any(lower::contains) -> Unit
                strongTotalWords.any(lower::contains) -> strongCandidates += amount to line
                weakTotalWords.any(lower::contains) -> weakCandidates += amount to line
            }
        }

        val picked = strongCandidates.maxByOrNull { it.first }
            ?: weakCandidates.maxByOrNull { it.first }
            ?: allAmounts.maxByOrNull { it.first }

        val confidence = when {
            picked == null -> 0
            strongCandidates.isNotEmpty() -> 94
            weakCandidates.isNotEmpty() -> 82
            else -> 58
        }

        val items = lines.mapNotNull { line ->
            val amount = amountRegex.find(line)?.groupValues?.getOrNull(1)?.let(::parseAmountCents)
                ?: return@mapNotNull null
            val name = line.substringBefore(amountRegex.find(line)?.value ?: "").trim(' ', '.', '-', ':')
            if (name.length < 3 || name.all(Char::isDigit)) null
            else ReceiptLineItem(name = name, amountCents = amount)
        }

        return ReceiptScanResult(
            merchant = merchant,
            totalCents = picked?.first,
            dateText = dateRegex.find(rawText)?.value,
            items = items,
            rawText = rawText,
            confidence = confidence,
        )
    }

    fun parseAmountCents(value: String): Long? {
        val cleaned = value.replace(",", "").trim()
        val decimal = cleaned.toDoubleOrNull() ?: return null
        return kotlin.math.round(decimal * 100).toLong().takeIf { it > 0 }
    }
}
