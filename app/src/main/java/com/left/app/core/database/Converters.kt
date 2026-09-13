package com.left.app.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Room type converters.
 *
 * Date/time storage policy (PRD §16):
 *  - [LocalDate] (transaction dates, billing dates) is stored as an INTEGER
 *    epoch day. Calendar dates are timezone-free; the user's zone is applied
 *    only when a "current month" is derived from an injected Clock.
 *  - [Instant] (created/updated timestamps) is stored as INTEGER epoch millis UTC.
 *
 * Enum properties (TransactionType, TransactionSource, CategoryType,
 * BillingCycle, IncomeFrequency) are stored as TEXT by Room's built-in enum
 * support, so no converters are declared for them.
 */
class Converters {

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantToEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)
}
