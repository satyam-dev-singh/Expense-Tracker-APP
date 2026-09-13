package com.left.app.core.common

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

/**
 * Half-open date range [startDate, endExclusiveDate) covering exactly one calendar
 * month. This is the ONLY way monthly filtering is computed (PRD §16):
 *
 *  - String comparison of dates is never used.
 *  - January → December rollover, leap years and different month lengths are
 *    handled by [YearMonth].
 *  - Timezone boundaries are handled by deriving "the current month" from an
 *    injected [Clock] (which carries a [java.time.ZoneId]); device locale does
 *    not affect calendar math, only display formatting.
 *  - Dates are stored as epoch days (see Converters.kt), so range queries are
 *    exact integer comparisons: `transaction_date >= start AND < end`.
 */
data class MonthRange(val yearMonth: YearMonth) {

    /** First day of the month (inclusive). */
    val startDate: LocalDate = yearMonth.atDay(1)

    /** First day of the NEXT month (exclusive upper bound). */
    val endExclusiveDate: LocalDate = yearMonth.plusMonths(1).atDay(1)

    val startEpochDay: Long = startDate.toEpochDay()
    val endExclusiveEpochDay: Long = endExclusiveDate.toEpochDay()

    val year: Int get() = yearMonth.year

    /** 1..12, matching [java.time.Month.getValue]. */
    val month: Int get() = yearMonth.monthValue

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && date.isBefore(endExclusiveDate)

    fun containsEpochDay(epochDay: Long): Boolean =
        epochDay in startEpochDay until endExclusiveEpochDay

    companion object {
        /** @throws IllegalArgumentException if [month] is not in 1..12. */
        fun of(year: Int, month: Int): MonthRange {
            require(month in 1..12) { "month must be in 1..12, was $month" }
            return MonthRange(YearMonth.of(year, month))
        }

        fun of(yearMonth: YearMonth): MonthRange = MonthRange(yearMonth)

        /** The current month in the timezone carried by [clock]. */
        fun current(clock: Clock = Clock.systemDefaultZone()): MonthRange =
            MonthRange(YearMonth.now(clock))
    }
}
