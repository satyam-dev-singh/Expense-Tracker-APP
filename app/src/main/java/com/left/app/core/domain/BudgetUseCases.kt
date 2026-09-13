package com.left.app.core.domain

import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Budget pacing logic (PRD FR-06, Phase 4).
 *
 * Budget remaining = budget − expenses — a DIFFERENT concept from actual money
 * left (income − expenses); the two are never conflated (PRD §15).
 */

/** Spending-pace status for a month with a budget (FR-06 warning thresholds). */
enum class BudgetStatus { ON_TRACK, WARNING, EXCEEDED }

/** Usage at or above this percentage surfaces a warning (PRD FR-06). */
const val BUDGET_WARNING_THRESHOLD_PERCENT: Double = 80.0

/** Maps budget usage to a status. 100%+ is exceeded; 80%+ warns. */
fun evaluateBudgetStatus(usagePercent: Double): BudgetStatus = when {
    usagePercent >= 100.0 -> BudgetStatus.EXCEEDED
    usagePercent >= BUDGET_WARNING_THRESHOLD_PERCENT -> BudgetStatus.WARNING
    else -> BudgetStatus.ON_TRACK
}

/**
 * Daily allowance for the CURRENT month: the budget remaining divided by the
 * days left including today (PRD FR-06 "daily allowance calculation").
 *
 * Rules:
 *  - null when no budget exists, or when [month] is not the month of [today]
 *    (an allowance for a past/future month is meaningless).
 *  - clamped at zero when overspent — the allowance is a "safe to spend"
 *    figure; negative spending advice is not useful.
 *  - integer minor-unit division (floor) — fractional paise are never invented.
 */
fun dailyAllowance(
    budgetLimit: Money?,
    expenses: Money,
    today: LocalDate,
    month: YearMonth,
): Money? {
    if (budgetLimit == null) return null
    if (YearMonth.from(today) != month) return null
    val remaining = budgetLimit - expenses
    val safeRemaining = if (remaining.isNegative) Money.ZERO else remaining
    val daysRemaining = month.lengthOfMonth() - today.dayOfMonth + 1
    return Money.ofMinorUnits(safeRemaining.minorUnits / daysRemaining)
}

/** Use-case wrapper around [dailyAllowance] (repository reads + injected clock). */
class CalculateDailyAllowance @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val clock: Clock,
) {
    /**
     * @return the daily allowance for ([year], [month]), or null when there is
     * no budget or the month is not the current one.
     */
    suspend operator fun invoke(year: Int, month: Int): Money? {
        val budget = monthlyBudgetRepository.getBudget(year, month) ?: return null
        val expenses = transactionRepository.getMonthlyTotals(year, month).expenses
        return dailyAllowance(budget.totalLimit, expenses, LocalDate.now(clock), YearMonth.of(year, month))
    }
}
