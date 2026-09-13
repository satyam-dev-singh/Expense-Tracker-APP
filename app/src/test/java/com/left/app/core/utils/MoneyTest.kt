package com.left.app.core.utils

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Money tests (PRD §19): integer arithmetic, zero, large values, decimal
 * currency input. ₹100.50 is 10050 paise — never Float/Double (PRD §10).
 */
class MoneyTest {

    // ---- Integer arithmetic ----

    @Test
    fun `plus and minus use integer minor units`() {
        val a = Money.ofMinorUnits(10_050) // ₹100.50
        val b = Money.ofMinorUnits(25_025) // ₹250.25
        assertEquals(35_075, (a + b).minorUnits)
        assertEquals(-14_975, (a - b).minorUnits)
        assertEquals(14_975, (b - a).minorUnits)
    }

    @Test
    fun `sum folds with integer arithmetic`() {
        val amounts = listOf(
            Money.ofMinorUnits(1),
            Money.ofMinorUnits(2),
            Money.ofMinorUnits(3),
        )
        assertEquals(6L, amounts.sum().minorUnits)
        assertEquals(Money.ZERO, emptyList<Money>().sum())
    }

    @Test
    fun `overflow is detected, never silently wrapped`() {
        val huge = Money.ofMinorUnits(Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) { huge + Money.ofMinorUnits(1) }
        assertThrows(ArithmeticException::class.java) { Money.ofMinorUnits(Long.MIN_VALUE) - Money.ofMinorUnits(1) }
    }

    // ---- Zero and sign ----

    @Test
    fun `zero behaves correctly`() {
        assertTrue(Money.ZERO.isZero)
        assertFalse(Money.ZERO.isPositive)
        assertFalse(Money.ZERO.isNegative)
        val amount = Money.ofMinorUnits(500)
        assertEquals(amount, amount + Money.ZERO)
        assertEquals(amount, amount - Money.ZERO)
    }

    @Test
    fun `sign helpers and ordering`() {
        assertTrue(Money.ofMinorUnits(1).isPositive)
        assertTrue(Money.ofMinorUnits(-1).isNegative)
        assertTrue(Money.ofMinorUnits(200) > Money.ofMinorUnits(100))
        assertEquals(0, Money.ofMinorUnits(5).compareTo(Money.ofMinorUnits(5)))
    }

    // ---- Large values ----

    @Test
    fun `large values stay exact`() {
        // ₹1,00,00,00,00,000.00 (one lakh crore) = 10^14 paise — far beyond Float precision.
        val large = Money.ofMinorUnits(10_000_000_000_000L)
        val result = large + large - Money.ofMinorUnits(1)
        assertEquals(19_999_999_999_999L, result.minorUnits)
    }

    // ---- Decimal currency input ----

    @Test
    fun `parse converts rupee strings to paise`() {
        assertEquals(10_050L, Money.parse("100.50", "INR").minorUnits) // the PRD example
        assertEquals(25_000L, Money.parse("250", "INR").minorUnits)
        assertEquals(99L, Money.parse("0.99", "INR").minorUnits)
        assertEquals(4_200L, Money.parse("  42  ", "INR").minorUnits)
    }

    @Test
    fun `parse accepts currency symbols and grouping separators`() {
        assertEquals(10_000_050L, Money.parse("₹1,00,000.50", "INR").minorUnits) // Indian grouping
        assertEquals(123_456L, Money.parse("1,234.56", "INR").minorUnits) // Western grouping
        assertEquals(1_000L, Money.parse("Rs. 10", "INR").minorUnits)
    }

    @Test
    fun `parse handles negative amounts`() {
        assertEquals(-5_000L, Money.parse("-50", "INR").minorUnits)
    }

    @Test
    fun `parse rejects over-precise input instead of rounding`() {
        assertThrows(MoneyParseException::class.java) { Money.parse("10.005", "INR") }
    }

    @Test
    fun `parse rejects blank and malformed input`() {
        assertThrows(MoneyParseException::class.java) { Money.parse("", "INR") }
        assertThrows(MoneyParseException::class.java) { Money.parse("   ", "INR") }
        assertThrows(MoneyParseException::class.java) { Money.parse("abc", "INR") }
        assertThrows(MoneyParseException::class.java) { Money.parse("1.2.3", "INR") }
        assertThrows(MoneyParseException::class.java) { Money.parse("-", "INR") }
    }

    @Test
    fun `parse respects currency fraction digits`() {
        assertEquals(100L, Money.parse("100", "JPY").minorUnits) // JPY has 0 fraction digits
        assertThrows(MoneyParseException::class.java) { Money.parse("100.5", "JPY") }
    }

    @Test
    fun `parse rejects invalid currency codes`() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("10", "XYZ") }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("10", "inr") } // must be uppercase ISO
    }

    @Test
    fun `currency validation`() {
        assertTrue(CurrencyUtils.isValidCurrencyCode("INR"))
        assertTrue(CurrencyUtils.isValidCurrencyCode("USD"))
        assertFalse(CurrencyUtils.isValidCurrencyCode("inr"))
        assertFalse(CurrencyUtils.isValidCurrencyCode("XYZ"))
        assertFalse(CurrencyUtils.isValidCurrencyCode("IN"))
    }

    @Test
    fun `format renders the major-unit value without changing the stored amount`() {
        val formatted = Money.ofMinorUnits(10_050).format("INR", Locale.forLanguageTag("en-IN"))
        assertTrue(formatted.contains("100.50"))
        assertEquals(10_050L, Money.ofMinorUnits(10_050).minorUnits)
    }
}
