package com.example.sortmate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun `grand total wins over subtotal and change`() {
        val text = """
            PUREGOLD
            Chicken breast 250.00
            Rice 55.50
            SUBTOTAL 305.50
            VAT 30.00
            GRAND TOTAL 335.50
            CASH 500.00
            CHANGE 164.50
        """.trimIndent()

        val result = ReceiptParser.parse(text)

        assertEquals(33_550L, result.totalCents)
        assertEquals(94, result.confidence)
        assertEquals("PUREGOLD", result.merchant)
        assertTrue(result.items.any { it.name.contains("Chicken") })
    }

    @Test
    fun `php prefix and thousands separators are parsed`() {
        val text = """
            SM SUPERMARKET
            GRAND TOTAL PHP 1,234.50
        """.trimIndent()

        val result = ReceiptParser.parse(text)

        assertEquals(123_450L, result.totalCents)
        assertEquals(94, result.confidence)
    }

    @Test
    fun `falls back to the largest amount when no total keyword exists`() {
        val text = """
            SARI SARI STORE
            Snack 20.00
            Drink 35.50
            Candy 12.00
        """.trimIndent()

        val result = ReceiptParser.parse(text)

        assertEquals(3_550L, result.totalCents)
        assertEquals(58, result.confidence)
    }

    @Test
    fun `weak total keyword is used when grand total is missing`() {
        val text = """
            CANTEEN
            Meal 90.00
            TOTAL 90.00
        """.trimIndent()

        val result = ReceiptParser.parse(text)

        assertEquals(9_000L, result.totalCents)
        assertEquals(82, result.confidence)
    }

    @Test
    fun `receipt without amounts returns zero confidence`() {
        val result = ReceiptParser.parse("HELLO WORLD\nTHANK YOU")

        assertNull(result.totalCents)
        assertEquals(0, result.confidence)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `date is detected from common slash format`() {
        val text = """
            ALING NENA STORE
            Date 12/03/2026
            GRAND TOTAL 150.00
        """.trimIndent()

        val result = ReceiptParser.parse(text)

        assertEquals("12/03/2026", result.dateText)
    }

    @Test
    fun `parseAmountCents rounds decimals`() {
        assertEquals(12_350L, ReceiptParser.parseAmountCents("123.50"))
        assertEquals(100_000L, ReceiptParser.parseAmountCents("1,000"))
        assertNull(ReceiptParser.parseAmountCents("not money"))
    }
}
