package com.left.app.feature.analytics

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeSubscriptionRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.model.BillingCycle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AnalyticsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))
    private lateinit var transactions: FakeTransactionRepository
    private lateinit var categories: FakeCategoryRepository
    private lateinit var subscriptions: FakeSubscriptionRepository
    @Before fun setUp() = runTest { transactions = FakeTransactionRepository(); categories = FakeCategoryRepository(clock); subscriptions = FakeSubscriptionRepository(clock); categories.ensureDefaultCategories() }
    private fun vm() = AnalyticsViewModel(transactions, categories, subscriptions, FakeUserProfileRepository(clock), clock)
    private suspend fun add(type: TransactionType, amount: Long, date: LocalDate, categoryId: String? = null, recurring: Boolean = false) { transactions.insert(Transaction(UUID.randomUUID().toString(), type, Money.ofMinorUnits(amount), "INR", categoryId, null, null, date, Instant.now(clock), Instant.now(clock), TransactionSource.MANUAL, recurring)) }
    @Test fun `empty month explains that more data is needed`() = runTest { val state = vm().uiState.first { !it.loading }; assertEquals("March 2024", state.monthLabel); assertTrue(state.categoryDistribution.isEmpty()); assertTrue(state.insightSummary.contains("No spending recorded")) }
    @Test fun `category distribution is sorted and percent based on expense total`() = runTest { add(TransactionType.INCOME, 5_000_000, LocalDate.of(2024, 3, 1)); add(TransactionType.EXPENSE, 1_000_000, LocalDate.of(2024, 3, 3), "default-food"); add(TransactionType.EXPENSE, 500_000, LocalDate.of(2024, 3, 4), "default-travel"); add(TransactionType.EXPENSE, 500_000, LocalDate.of(2024, 3, 5), "default-food"); val state = vm().uiState.first { !it.loading }; assertEquals("Food", state.topCategoryName); assertEquals(1_500_000L, state.categoryDistribution.first().amount.minorUnits); assertEquals(75.0, state.categoryDistribution.first().percent, 0.001) }
    @Test fun `month over month compares expenses only`() = runTest { add(TransactionType.EXPENSE, 1_000_000, LocalDate.of(2024, 2, 15)); add(TransactionType.EXPENSE, 1_250_000, LocalDate.of(2024, 3, 10)); val state = vm().uiState.first { !it.loading }; assertEquals(1_000_000L, state.previousMonthExpenses.minorUnits); assertEquals(250_000L, state.monthOverMonthDelta.minorUnits); assertEquals(25.0, state.monthOverMonthPercent!!, 0.001) }
    @Test fun `recurring total includes recurring transactions and active subscriptions`() = runTest { add(TransactionType.EXPENSE, 500_000, LocalDate.of(2024, 3, 5), recurring = true); subscriptions.create("Music", Money.ofMinorUnits(90_000), "INR", null, BillingCycle.MONTHLY, LocalDate.of(2024, 3, 20), true, true); subscriptions.create("Old", Money.ofMinorUnits(10_000), "INR", null, BillingCycle.MONTHLY, LocalDate.of(2024, 3, 20), true, false); val state = vm().uiState.first { !it.loading }; assertEquals(590_000L, state.recurringTotal.minorUnits) }
    @Test fun `month navigation rebuilds trend window`() = runTest { add(TransactionType.EXPENSE, 100_000, LocalDate.of(2024, 2, 15)); add(TransactionType.EXPENSE, 200_000, LocalDate.of(2024, 3, 15)); val viewModel = vm(); val states = mutableListOf<AnalyticsUiState>(); backgroundScope.launch { viewModel.uiState.collect { states.add(it) } }; advanceUntilIdle(); assertEquals("Mar 24", states.last().trends.last().monthLabel); viewModel.onPreviousMonth(); advanceUntilIdle(); assertEquals("February 2024", states.last().monthLabel); assertEquals("Feb 24", states.last().trends.last().monthLabel) }
}
