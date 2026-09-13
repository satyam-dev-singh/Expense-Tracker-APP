package com.left.app.core.domain

import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.model.budgetUsagePercentage
import com.left.app.core.utils.Money
import javax.inject.Inject

/**
 * Calculation use cases (PRD §14/§15, Technical Architecture §4).
 *
 * Two DIFFERENT concepts are computed here and must never be conflated in UI:
 *  - ACTUAL remaining  = income − expenses  ("Money left")
 *  - BUDGET remaining  = budget − expenses  ("Budget remaining")
 *
 * All money math is integer minor units via [Money]. Double appears only as
 * the derived budget-usage *percentage* display metric.
 */

/** SUM(INCOME transactions) for the selected month. */
class CalculateMonthlyIncome @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(year: Int, month: Int): Money =
        transactionRepository.getMonthlyTotals(year, month).income
}

/** SUM(EXPENSE transactions) for the selected month. */
class CalculateMonthlyExpenses @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(year: Int, month: Int): Money =
        transactionRepository.getMonthlyTotals(year, month).expenses
}

/** ACTUAL money left: income − expenses (NOT budget-based). */
class CalculateRemainingMoney @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(year: Int, month: Int): Money {
        val totals = transactionRepository.getMonthlyTotals(year, month)
        return totals.income - totals.expenses
    }
}

/**
 * BUDGET remaining: monthly budget − expenses. Never present this as actual
 * cash remaining (PRD §15).
 *
 * @return null when no budget is configured for the month (UI hides the block).
 */
class CalculateBudgetRemaining @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
) {
    suspend operator fun invoke(year: Int, month: Int): Money? {
        val budget = monthlyBudgetRepository.getBudget(year, month) ?: return null
        val expenses = transactionRepository.getMonthlyTotals(year, month).expenses
        return budget.totalLimit - expenses
    }
}

/**
 * Percentage of the monthly budget already consumed (0–100+, 2-decimal
 * half-up rounding, e.g. 66.67). Delegates to the shared [budgetUsagePercentage]
 * helper so ViewModels and this use case compute identically.
 *
 * @return null when no budget is configured or the budget is not positive.
 */
class CalculateBudgetUsagePercentage @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
) {
    suspend operator fun invoke(year: Int, month: Int): Double? {
        val budget = monthlyBudgetRepository.getBudget(year, month) ?: return null
        val expenses = transactionRepository.getMonthlyTotals(year, month).expenses
        return budgetUsagePercentage(expenses, budget.totalLimit)
    }
}
