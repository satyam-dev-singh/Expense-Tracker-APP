package com.left.app.core.domain

import com.left.app.core.data.fake.FakeMonthlyBudgetRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Budget pacing tests (PRD FR-06, Phase 4): daily allowance math (days
 * remaining including today, leap months, clamped overspend), warning
 * thresholds (80% warn / 100% exceeded), current-month gating.
 */
class BudgetUseCasesTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))
    private val today: LocalDate = LocalDate.of(2024, 3, 10)

    // ---- evaluateBudgetStatus thresholds ----

    @Test
    fun `status thresholds - under 80 on track, 80 warns, 100 exceeded`() {
        assertEquals(BudgetStatus.ON_TRACK, evaluateBudgetStatus(0.0))
        assertEquals(BudgetStatus.ON_TRACK, evaluateBudgetStatus(79.99))
        assertEquals(BudgetStatus.WARNING, evaluateBudgetStatus(80.0))
        assertEquals(BudgetStatus.WARNING, evaluateBudgetStatus(99.99))
        assertEquals(BudgetStatus.EXCEEDED, evaluateBudgetStatus(100.0))
        assertEquals(BudgetStatus.EXCEEDED, evaluateBudgetStatus(150.0))
    }

    // ---- dailyAllowance (pure function) ----

    @Test
    fun `allowance divides budget remaining by days left including today`() {
        // March 2024 has 31 days; on the 10th there are 22 days left (10th..31st).
        // ₹10,000 remaining / 22 = 45454.54.. → 45454 paise (floor).
        val allowance = dailyAllowance(
            budgetLimit = Money.ofMinorUnits(3_000_000),
            expenses = Money.ofMinorUnits(2_000_000),
            today = today,
            month = YearMonth.of(2024, 3),
        )
        assertEquals(1_000_000L / 22, allowance?.minorUnits)
        assertEquals(45_454L, allowance?.minorUnits)
    }

    @Test
    fun `allowance on the first day uses the whole month`() {
        val allowance = dailyAllowance(
            budgetLimit = Money.ofMinorUnits(3_100_000), // ₹31,000
            expenses = Money.ZERO,
            today = LocalDate.of(2024, 3, 1),
            month = YearMonth.of(2024, 3),
        )
        assertEquals(100_000L, allowance?.minorUnits) // ₹1,000/day for 31 days
    }

    @Test
    fun `allowance on the last day is whatever is left`() {
        val allowance = dailyAllowance(
            budgetLimit = Money.ofMinorUnits(3_000_000),
            expenses = Money.ofMinorUnits(2_950_000),
            today = LocalDate.of(2024, 3, 31),
            month = YearMonth.of(2024, 3),
        )
        assertEquals(50_000L, allowance?.minorUnits)
    }

    @Test
    fun `leap february has 29 days in the allowance window`() {
        val allowance = dailyAllowance(
            budgetLimit = Money.ofMinorUnits(2_900_000), // ₹29,000
            expenses = Money.ZERO,
            today = LocalDate.of(2024, 2, 10),
            month = YearMonth.of(2024, 2),
        )
        // 20 days remain (10th..29th): 2,900,000 / 20 = 145,000 exactly.
        assertEquals(145_000L, allowance?.minorUnits)
    }

    @Test
    fun `overspending clamps the allowance to zero`() {
        val allowance = dailyAllowance(
            budgetLimit = Money.ofMinorUnits(1_000_000),
            expenses = Money.ofMinorUnits(1_500_000),
            today = today,
            month = YearMonth.of(2024, 3),
        )
        assertEquals(Money.ZERO, allowance)
    }

    @Test
    fun `no budget means no allowance`() {
        assertNull(dailyAllowance(null, Money.ofMinorUnits(1), today, YearMonth.of(2024, 3)))
    }

    @Test
    fun `allowance only exists for the current month`() {
        assertNull(dailyAllowance(Money.ofMinorUnits(1_000_000), Money.ZERO, today, YearMonth.of(2024, 2)))
        assertNull(dailyAllowance(Money.ofMinorUnits(1_000_000), Money.ZERO, today, YearMonth.of(2024, 4)))
    }

    // ---- CalculateDailyAllowance (use case over fakes) ----

    private suspend fun addExpense(amountMinor: Long, date: LocalDate, repo: FakeTransactionRepository) {
        repo.insert(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                amount = Money.ofMinorUnits(amountMinor),
                currencyCode = "INR",
                categoryId = null,
                merchant = null,
                note = null,
                date = date,
                createdAt = Instant.now(clock),
                updatedAt = Instant.now(clock),
                source = TransactionSource.MANUAL,
                isRecurring = false,
            ),
        )
    }

    @Test
    fun `use case reads repositories and computes the current month allowance`() = runTest {
        val transactions = FakeTransactionRepository()
        val budgets = FakeMonthlyBudgetRepository(clock)
        addExpense(2_000_000, LocalDate.of(2024, 3, 7), transactions)
        budgets.setBudget(2024, 3, Money.ofMinorUnits(3_000_000))

        val allowance = CalculateDailyAllowance(transactions, budgets, clock)(2024, 3)
        assertEquals(45_454L, allowance?.minorUnits)
    }

    @Test
    fun `use case returns null without a budget or for a non-current month`() = runTest {
        val transactions = FakeTransactionRepository()
        val budgets = FakeMonthlyBudgetRepository(clock)
        val useCase = CalculateDailyAllowance(transactions, budgets, clock)

        assertNull(useCase(2024, 3)) // no budget
        budgets.setBudget(2024, 2, Money.ofMinorUnits(1_000_000))
        assertNull(useCase(2024, 2)) // budget exists but not the current month
    }
}
