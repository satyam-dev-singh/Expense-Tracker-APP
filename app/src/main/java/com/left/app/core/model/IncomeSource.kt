package com.left.app.core.model

import com.left.app.core.utils.Money
import java.time.Instant
import java.time.LocalDate

enum class IncomeFrequency { WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY, ONE_TIME }

/**
 * Domain model for a configured income source (e.g. salary).
 *
 * Currency follows the user's profile currency (PRD/master data model lists no
 * per-source currency). [nextDate] is nullable for one-time or irregular income.
 */
data class IncomeSource(
    val id: String,
    val name: String,
    val amount: Money,
    val frequency: IncomeFrequency,
    val nextDate: LocalDate?,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
