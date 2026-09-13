package com.left.app.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Month-handling tests (PRD §16/§19): January→December transition, leap years,
 * different month lengths, timezone boundaries. Monthly filtering never uses
 * string comparison — it is an exact half-open epoch-day range.
 */
class MonthRangeTest {

    @Test
    fun `december rolls into january of the next year`() {
        val december = MonthRange.of(2024, 12)
        assertEquals(LocalDate.of(2024, 12, 1), december.startDate)
        assertEquals(LocalDate.of(2025, 1, 1), december.endExclusiveDate)
        assertTrue(december.contains(LocalDate.of(2024, 12, 31)))
        assertFalse(december.contains(LocalDate.of(2025, 1, 1)))
    }

    @Test
    fun `leap year february has 29 days and contains leap day`() {
        val feb2024 = MonthRange.of(2024, 2)
        assertEquals(LocalDate.of(2024, 2, 1), feb2024.startDate)
        assertEquals(LocalDate.of(2024, 3, 1), feb2024.endExclusiveDate)
        assertEquals(29L, feb2024.endExclusiveEpochDay - feb2024.startEpochDay)
        assertTrue(feb2024.contains(LocalDate.of(2024, 2, 29)))
    }

    @Test
    fun `non-leap february has 28 days`() {
        val feb2023 = MonthRange.of(2023, 2)
        assertEquals(LocalDate.of(2023, 3, 1), feb2023.endExclusiveDate)
        assertEquals(28L, feb2023.endExclusiveEpochDay - feb2023.startEpochDay)
    }

    @Test
    fun `month lengths differ correctly`() {
        val january = MonthRange.of(2024, 1)
        val april = MonthRange.of(2024, 4)
        assertEquals(31L, january.endExclusiveEpochDay - january.startEpochDay)
        assertEquals(30L, april.endExclusiveEpochDay - april.startEpochDay)
    }

    @Test
    fun `range is half-open - start inclusive, end exclusive`() {
        val march = MonthRange.of(2024, 3)
        assertTrue(march.contains(LocalDate.of(2024, 3, 1))) // first day included
        assertTrue(march.contains(LocalDate.of(2024, 3, 31))) // last day included
        assertFalse(march.contains(LocalDate.of(2024, 4, 1))) // next month excluded
        assertFalse(march.contains(LocalDate.of(2024, 2, 29))) // previous month excluded
    }

    @Test
    fun `current month respects the timezone carried by the clock`() {
        // 2024-02-29 19:00 UTC is already 2024-03-01 00:30 in Asia/Kolkata.
        val instant = Instant.parse("2024-02-29T19:00:00Z")
        val utc = MonthRange.current(Clock.fixed(instant, ZoneOffset.UTC))
        val kolkata = MonthRange.current(Clock.fixed(instant, ZoneId.of("Asia/Kolkata")))
        assertEquals(YearMonth.of(2024, 2), utc.yearMonth)
        assertEquals(YearMonth.of(2024, 3), kolkata.yearMonth)
    }

    @Test
    fun `invalid months are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { MonthRange.of(2024, 0) }
        assertThrows(IllegalArgumentException::class.java) { MonthRange.of(2024, 13) }
    }
}
