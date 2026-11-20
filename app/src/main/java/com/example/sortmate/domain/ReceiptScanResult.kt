package com.example.sortmate.domain

/** One parsed line item from a receipt. */
data class ReceiptLineItem(
    val name: String,
    val amountCents: Long,
)

/** Output of the on-device OCR + parsing pipeline. */
data class ReceiptScanResult(
    val merchant: String?,
    val totalCents: Long?,
    val dateText: String?,
    val items: List<ReceiptLineItem>,
    val rawText: String,
    val confidence: Int,
) {
    val hasTotal: Boolean get() = totalCents != null && totalCents > 0
}
