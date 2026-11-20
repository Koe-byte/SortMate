package com.example.sortmate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    @Test
    fun `even split distributes leftover cents and preserves the total`() {
        val shares = SplitCalculator.evenShares(100, 3)

        assertEquals(listOf(34L, 33L, 33L), shares)
        assertEquals(100L, shares.sum())
    }

    @Test
    fun `larger split preserves the total`() {
        val shares = SplitCalculator.evenShares(123_457, 6)

        assertEquals(123_457L, shares.sum())
        assertEquals(20_577L, shares.first())
        assertEquals(20_576L, shares.last())
    }

    @Test
    fun `single participant takes everything`() {
        assertEquals(listOf(9_999L), SplitCalculator.evenShares(9_999, 1))
    }

    @Test
    fun `invalid inputs return empty shares`() {
        assertTrue(SplitCalculator.evenShares(0, 3).isEmpty())
        assertTrue(SplitCalculator.evenShares(-1, 3).isEmpty())
        assertTrue(SplitCalculator.evenShares(100, 0).isEmpty())
    }

    @Test
    fun `shareFor returns the first share`() {
        assertEquals(34L, SplitCalculator.shareFor(100, 3))
        assertEquals(0L, SplitCalculator.shareFor(100, 0))
    }
}
