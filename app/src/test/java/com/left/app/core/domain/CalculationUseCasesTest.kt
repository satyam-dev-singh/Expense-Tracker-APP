package com.left.app.core.domain

import com.left.app.core.data.fake.FakeMonthlyBudgetRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Monthly calculation tests (PRD §19), covering the required scenarios:
 *  - Income ₹50,000 / Expenses ₹20,000 → Remaining ₹30,000
 *  - Budget ₹30,000 / Expenses ₹20,000 → Budget remaining ₹10,000, usage 66.67%
 *  - no transactions, only expenses, only income, multiple categories,
 *    previous/next month exclusion, month boundary, leap day.
 *
 * All amounts below are paise (integer minor units).
 */
class CalculationUseCasesTest {

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var budgetRepository: FakeMonthlyBudgetRepository

    private lateinit var monthlyIncome: CalculateMonthlyIncome
    private lateinit var monthlyExpenses: CalculateMonthlyExpenses
    private lateinit var remainingMoney: CalculateRemainingMoney
    private lateinit var budgetRemaining: CalculateBudgetRemaining
    private lateinit var budgetUsage: CalculateBudgetUsagePercentage

    @Before
    fun setUp() {
        transactionRepository = FakeTransactionRepository()
        budgetRepository = FakeMonthlyBudgetRepository()
        monthlyIncome = CalculateMonthlyIncome(transactionRepository)
        monthlyExpenses = CalculateMonthlyExpenses(transactionRepository)
        remainingMoney = CalculateRemainingMoney(transactionRepository)
        budgetRemaining = CalculateBudgetRemaining(transactionRepository, budgetRepository)
        budgetUsage = CalculateBudgetUsagePercentage(transactionRepository, budgetRepository)
    }

    private suspend fun add(
        type: TransactionType,
        amountMinor: Long,
        date: LocalDate,
        categoryId: String? = null,
    ) {
        transactionRepository.insert(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = type,
                amount = Money.ofMinorUnits(amountMinor),
                currencyCode = "INR",
                categoryId = categoryId,
                merchant = null,
                note = null,
                date = date,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
                source = TransactionSource.MANUAL,
                isRecurring = false,
            ),
        )
    }

    // ---- PRD reference scenario ----

    @Test
    fun `income 50000 minus expenses 20000 leaves 30000`() = runTest {
        add(TransactionType.INCOME, 5_000_000, LocalDate.of(2024, 3, 5)) // ₹50,000
        add(TransactionType.EXPENSE, 2_000_000, LocalDate.of(2024, 3, 7)) // ₹20,000

        assertEquals(5_000_000L, monthlyIncome(2024, 3).minorUnits)
        assertEquals(2_000_000L, monthlyExpenses(2024, 3).minorUnits)
        assertEquals(3_000_000L, remainingMoney(2024, 3).minorUnits) // ₹30,000 actual money left
    }

    @Test
    fun `budget 30000 with expenses 20000 leaves 10000 and 66-67 percent used`() = runTest {
        add(TransactionType.EXPENSE, 2_000_000, LocalDate.of(2024, 3, 7)) // ₹20,000
        budgetRepository.setBudget(2024, 3, Money.ofMinorUnits(3_000_000)) // ₹30,000

        // Budget remaining — a DIFFERENT concept from actual money left (PRD §15).
        assertEquals(1_000_000L, budgetRemaining(2024, 3)?.minorUnits) // ₹10,000
        assertEquals(66.67, budgetUsage(2024, 3)!!, 0.001)
    }

    // ---- Edge cases ----

    @Test
    fun `no transactions means zero totals`() = runTest {
        assertEquals(Money.ZERO, monthlyIncome(2024, 3))
        assertEquals(Money.ZERO, monthlyExpenses(2024, 3))
        assertEquals(Money.ZERO, remainingMoney(2024, 3))
    }

    @Test
    fun `only expenses makes remaining negative - overspend is real`() = runTest {
        add(TransactionType.EXPENSE, 150_000, LocalDate.of(2024, 3, 2))
        assertEquals(-150_000L, remainingMoney(2024, 3).minorUnits)
    }

    @Test
    fun `only income means remaining equals income`() = runTest {
        add(TransactionType.INCOME, 250_000, LocalDate.of(2024, 3, 2))
        assertEquals(250_000L, remainingMoney(2024, 3).minorUnits)
        assertEquals(Money.ZERO, monthlyExpenses(2024, 3))
    }

    @Test
    fun `multiple categories all count toward the same totals`() = runTest {
        add(TransactionType.EXPENSE, 10_000, LocalDate.of(2024, 3, 1), categoryId = "default-food")
        add(TransactionType.EXPENSE, 20_000, LocalDate.of(2024, 3, 2), categoryId = "default-transport")
        add(TransactionType.EXPENSE, 30_000, LocalDate.of(2024, 3, 3), categoryId = null)
        assertEquals(60_000L, monthlyExpenses(2024, 3).minorUnits)
    }

    @Test
    fun `previous and next month transactions are excluded`() = runTest {
        add(TransactionType.EXPENSE, 1_000, LocalDate.of(2024, 2, 29)) // previous month (leap day)
        add(TransactionType.EXPENSE, 2_000, LocalDate.of(2024, 3, 15)) // this month
        add(TransactionType.EXPENSE, 4_000, LocalDate.of(2024, 4, 1)) // next month

        assertEquals(2_000L, monthlyExpenses(2024, 3).minorUnits)
        assertEquals(1_000L, monthlyExpenses(2024, 2).minorUnits)
        assertEquals(4_000L, monthlyExpenses(2024, 4).minorUnits)
    }

    @Test
    fun `month boundary - last day counts, first of next month does not`() = runTest {
        add(TransactionType.EXPENSE, 100, LocalDate.of(2024, 3, 31))
        add(TransactionType.EXPENSE, 200, LocalDate.of(2024, 4, 1))
        assertEquals(100L, monthlyExpenses(2024, 3).minorUnits)
    }

    @Test
    fun `leap day belongs to february`() = runTest {
        add(TransactionType.INCOME, 50_000, LocalDate.of(2024, 2, 29))
        assertEquals(50_000L, monthlyIncome(2024, 2).minorUnits)
        assertEquals(Money.ZERO, monthlyIncome(2024, 3))
        assertEquals(Money.ZERO, monthlyIncome(2023, 2)) // no leap day in 2023, and no data
    }

    // ---- Budget edge cases ----

    @Test
    fun `no budget configured returns null - UI must hide, not show fake zeros`() = runTest {
        add(TransactionType.EXPENSE, 1_000, LocalDate.of(2024, 3, 1))
        assertNull(budgetRemaining(2024, 3))
        assertNull(budgetUsage(2024, 3))
    }

    @Test
    fun `zero budget yields null usage - no division by zero`() = runTest {
        budgetRepository.setBudget(2024, 3, Money.ZERO)
        add(TransactionType.EXPENSE, 1_000, LocalDate.of(2024, 3, 1))
        assertNull(budgetUsage(2024, 3))
        assertEquals(-1_000L, budgetRemaining(2024, 3)?.minorUnits)
    }

    @Test
    fun `budget with no expenses yet is fully remaining with zero usage`() = runTest {
        budgetRepository.setBudget(2024, 3, Money.ofMinorUnits(3_000_000))
        assertEquals(3_000_000L, budgetRemaining(2024, 3)?.minorUnits)
        assertEquals(0.0, budgetUsage(2024, 3)!!, 0.0001)
    }

    @Test
    fun `spending beyond budget reports usage over 100 percent`() = runTest {
        budgetRepository.setBudget(2024, 3, Money.ofMinorUnits(1_000_000)) // ₹10,000
        add(TransactionType.EXPENSE, 1_500_000, LocalDate.of(2024, 3, 10)) // ₹15,000
        assertEquals(-500_000L, budgetRemaining(2024, 3)?.minorUnits)
        assertEquals(150.0, budgetUsage(2024, 3)!!, 0.0001)
    }
}
