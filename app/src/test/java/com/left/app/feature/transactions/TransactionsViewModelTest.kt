package com.left.app.feature.transactions

import com.left.app.core.data.fake.FakeCategoryRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Transactions list tests (PRD FR-11, S10): month scoping + navigation, all-time
 * text search, type/category/amount filters, day grouping with Today/Yesterday.
 */
class TransactionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var viewModel: TransactionsViewModel

    @Before
    fun setUp() = runTest {
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository(clock)
        categoryRepository.ensureDefaultCategories()
        add("today", TransactionType.EXPENSE, 35_000, LocalDate.of(2024, 3, 10), "default-food", "Tea stall")
        add("yesterday", TransactionType.EXPENSE, 12_000, LocalDate.of(2024, 3, 9), "default-transport", "Metro")
        add("salary", TransactionType.INCOME, 5_000_000, LocalDate.of(2024, 3, 1), null, "Salary")
        add("feb", TransactionType.EXPENSE, 1_000, LocalDate.of(2024, 2, 29), "default-food", "Leap day chai")
        add("apr", TransactionType.EXPENSE, 4_000, LocalDate.of(2024, 4, 1), "default-food", "April snack")
        viewModel = TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            userProfileRepository = FakeUserProfileRepository(clock),
            clock = clock,
        )
    }

    private suspend fun add(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        date: LocalDate,
        categoryId: String?,
        merchant: String,
    ) {
        transactionRepository.insert(
            Transaction(
                id = id,
                type = type,
                amount = Money.ofMinorUnits(amountMinor),
                currencyCode = "INR",
                categoryId = categoryId,
                merchant = merchant,
                note = null,
                date = date,
                createdAt = Instant.now(clock),
                updatedAt = Instant.now(clock),
                source = TransactionSource.MANUAL,
                isRecurring = false,
            ),
        )
    }

    private fun TestScope.collectStates(): MutableList<TransactionsUiState> {
        val states = mutableListOf<TransactionsUiState>()
        backgroundScope.launch { viewModel.uiState.collect { states.add(it) } }
        return states
    }

    @Test
    fun `defaults to current month grouped by day with labels`() = runTest {
        val states = collectStates()
        advanceUntilIdle()
        val state = states.last()

        assertFalse(state.loading)
        assertEquals("March 2024", state.monthLabel)
        assertEquals(listOf("today", "yesterday", "salary"), state.groups.flatMap { it.transactions }.map { it.id })
        assertEquals(listOf(LocalDate.of(2024, 3, 10), LocalDate.of(2024, 3, 9), LocalDate.of(2024, 3, 1)), state.groups.map { it.date })
        assertEquals("Today", state.groups[0].label)
        assertEquals("Yesterday", state.groups[1].label)
        assertEquals("Fri, 1 Mar", state.groups[2].label)
    }

    @Test
    fun `month navigation moves the window`() = runTest {
        val states = collectStates()
        advanceUntilIdle()

        viewModel.onPreviousMonth()
        advanceUntilIdle()
        assertEquals("February 2024", states.last().monthLabel)
        assertEquals(listOf("feb"), states.last().groups.flatMap { it.transactions }.map { it.id })

        viewModel.onNextMonth()
        viewModel.onNextMonth()
        advanceUntilIdle()
        assertEquals("April 2024", states.last().monthLabel)
        assertEquals(listOf("apr"), states.last().groups.flatMap { it.transactions }.map { it.id })
    }

    @Test
    fun `text query searches all time regardless of selected month`() = runTest {
        val states = collectStates()
        advanceUntilIdle()

        viewModel.onQueryChange("leap")
        advanceUntilIdle()

        val state = states.last()
        assertTrue(state.searchingAllTime)
        assertEquals(listOf("feb"), state.groups.flatMap { it.transactions }.map { it.id })
    }

    @Test
    fun `clearing the query returns to month scope`() = runTest {
        val states = collectStates()
        advanceUntilIdle()
        viewModel.onQueryChange("leap")
        advanceUntilIdle()
        viewModel.onQueryChange("")
        advanceUntilIdle()

        val state = states.last()
        assertFalse(state.searchingAllTime)
        assertEquals(listOf("today", "yesterday", "salary"), state.groups.flatMap { it.transactions }.map { it.id })
    }

    @Test
    fun `type filter keeps only expenses`() = runTest {
        val states = collectStates()
        advanceUntilIdle()
        viewModel.onTypeFilterChange(TransactionType.EXPENSE)
        advanceUntilIdle()

        assertEquals(listOf("today", "yesterday"), states.last().groups.flatMap { it.transactions }.map { it.id })
    }

    @Test
    fun `category filter keeps only matching rows`() = runTest {
        val states = collectStates()
        advanceUntilIdle()
        viewModel.onCategoryFilterChange("default-transport")
        advanceUntilIdle()

        assertEquals(listOf("yesterday"), states.last().groups.flatMap { it.transactions }.map { it.id })
    }

    @Test
    fun `amount range filter is inclusive and currency-aware`() = runTest {
        val states = collectStates()
        advanceUntilIdle()
        viewModel.onAmountMinChange("100") // ₹100.00
        viewModel.onAmountMaxChange("400") // ₹400.00
        advanceUntilIdle()

        // ₹350 included, ₹120 included, ₹50,000 income excluded.
        assertEquals(listOf("today", "yesterday"), states.last().groups.flatMap { it.transactions }.map { it.id })

        viewModel.onAmountMinChange("200")
        viewModel.onAmountMaxChange("300")
        advanceUntilIdle()
        assertTrue(states.last().groups.isEmpty())
    }

    @Test
    fun `malformed amount filters are ignored, not errors`() = runTest {
        val states = collectStates()
        advanceUntilIdle()
        viewModel.onAmountMinChange("abc")
        viewModel.onAmountMaxChange("1.2.3")
        advanceUntilIdle()

        assertEquals(3, states.last().groups.flatMap { it.transactions }.size)
    }

    @Test
    fun `empty month yields empty groups`() = runTest {
        val states = collectStates()
        advanceUntilIdle()
        viewModel.onNextMonth()
        viewModel.onNextMonth() // May 2024 — no data
        advanceUntilIdle()
        assertEquals("May 2024", states.last().monthLabel)
        assertTrue(states.last().groups.isEmpty())
    }
}
