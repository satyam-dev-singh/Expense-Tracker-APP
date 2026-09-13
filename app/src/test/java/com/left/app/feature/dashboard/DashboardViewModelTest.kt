package com.left.app.feature.dashboard

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeMonthlyBudgetRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import com.left.app.testutil.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Dashboard tests (PRD FR-02): actual money left from real transactions, budget
 * context never conflated with it (PRD §15), recent list, currency source.
 */
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var budgetRepository: FakeMonthlyBudgetRepository
    private lateinit var userProfileRepository: FakeUserProfileRepository
    private lateinit var categoryRepository: FakeCategoryRepository

    @Before
    fun setUp() {
        transactionRepository = FakeTransactionRepository()
        budgetRepository = FakeMonthlyBudgetRepository(clock)
        userProfileRepository = FakeUserProfileRepository(clock)
        categoryRepository = FakeCategoryRepository(clock)
        kotlinx.coroutines.runBlocking { categoryRepository.ensureDefaultCategories() }
    }

    private fun viewModel() = DashboardViewModel(
        transactionRepository = transactionRepository,
        monthlyBudgetRepository = budgetRepository,
        userProfileRepository = userProfileRepository,
        categoryRepository = categoryRepository,
        clock = clock,
    )

    private suspend fun add(type: TransactionType, amountMinor: Long, date: LocalDate) {
        transactionRepository.insert(
            Transaction(
                id = UUID.randomUUID().toString(),
                type = type,
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
    fun `empty month shows zero money left with current month label`() = runTest {
        val state = viewModel().uiState.first { !it.loading }
        assertEquals(Money.ZERO, state.moneyLeft)
        assertEquals(Money.ZERO, state.income)
        assertEquals(Money.ZERO, state.expenses)
        assertEquals("March 2024", state.monthLabel)
        assertEquals("INR", state.currencyCode) // default when no profile yet
        assertFalse(state.hasBudget)
        assertTrue(state.recentTransactions.isEmpty())
    }

    @Test
    fun `money left is income minus expenses for the current month`() = runTest {
        add(TransactionType.INCOME, 5_000_000, LocalDate.of(2024, 3, 5)) // ₹50,000
        add(TransactionType.EXPENSE, 2_000_000, LocalDate.of(2024, 3, 7)) // ₹20,000
        add(TransactionType.EXPENSE, 999, LocalDate.of(2024, 4, 1)) // next month — excluded

        val state = viewModel().uiState.first { !it.loading }
        assertEquals(5_000_000L, state.income.minorUnits)
        assertEquals(2_000_000L, state.expenses.minorUnits)
        assertEquals(3_000_000L, state.moneyLeft.minorUnits)
    }

    @Test
    fun `budget context is budget minus expenses - never conflated with money left`() = runTest {
        add(TransactionType.EXPENSE, 2_000_000, LocalDate.of(2024, 3, 7))
        budgetRepository.setBudget(2024, 3, Money.ofMinorUnits(3_000_000))

        val state = viewModel().uiState.first { !it.loading }
        assertTrue(state.hasBudget)
        assertEquals(1_000_000L, state.budgetRemaining?.minorUnits) // budget − expenses
        assertEquals(66.67, state.budgetUsagePercent!!, 0.001)
        assertEquals(-2_000_000L, state.moneyLeft.minorUnits) // actual, no income — different concept
    }

    @Test
    fun `recent shows the five newest transactions, newest first`() = runTest {
        repeat(6) { day -> add(TransactionType.EXPENSE, 100L + day, LocalDate.of(2024, 3, day + 1)) }
        add(TransactionType.EXPENSE, 42, LocalDate.of(2024, 2, 15)) // older month still eligible for recent

        val state = viewModel().uiState.first { !it.loading }
        assertEquals(5, state.recentTransactions.size)
        assertEquals(LocalDate.of(2024, 3, 6), state.recentTransactions.first().date)
    }

    @Test
    fun `currency comes from the profile when set`() = runTest {
        userProfileRepository.upsertProfile("Satyam", "USD", "en-US")
        val state = viewModel().uiState.first { !it.loading }
        assertEquals("USD", state.currencyCode)
    }
}
