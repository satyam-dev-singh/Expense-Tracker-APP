package com.left.app.core.model

import com.left.app.core.utils.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Domain model for the total spending budget of one calendar month.
 * Uniqueness is enforced at the database level (unique index on year+month).
 */
data class MonthlyBudget(
    val id: String,
    val year: Int,
    /** 1..12, matching [java.time.Month.getValue]. */
    val month: Int,
    val totalLimit: Money,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Aggregated totals for one month, computed from actual transaction rows.
 *
 * [remaining] is ACTUAL money left (income − expenses). It is a different
 * concept from budget remaining (budget − expenses) and the two must never be
 * conflated in UI (PRD §15, Technical Architecture §4).
 */
data class MonthlyTotals(
    val income: Money,
    val expenses: Money,
) {
    val remaining: Money get() = income - expenses
}

/**
 * Derived display metric: percentage of [limit] consumed by [expenses],
 * 2-decimal half-up (e.g. 66.67). Double is used ONLY for this display value —
 * never for money (PRD §10). Returns null when [limit] is not positive.
 *
 * Shared by CalculateBudgetUsagePercentage and reactive ViewModels so the rule
 * lives in exactly one place.
 */
fun budgetUsagePercentage(expenses: Money, limit: Money): Double? {
    if (!limit.isPositive) return null
    return BigDecimal(expenses.minorUnits)
        .multiply(BigDecimal(100))
        .divide(BigDecimal(limit.minorUnits), 2, RoundingMode.HALF_UP)
        .toDouble()
}
