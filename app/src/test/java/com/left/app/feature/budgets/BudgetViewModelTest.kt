package com.left.app.feature.budgets

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeMonthlyBudgetRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.domain.BudgetStatus
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Budget management tests (S14, PRD FR-06): load/prefill, rollover from the
 * previous month, save/clear semantics, per-category limits, validation.
 */
class BudgetViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var budgetRepository: FakeMonthlyBudgetRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var viewModel: BudgetViewModel

    @Before
    fun setUp() = runTest {
        transactionRepository = FakeTransactionRepository()
        budgetRepository = FakeMonthlyBudgetRepository(clock)
        categoryRepository = FakeCategoryRepository(clock)
        categoryRepository.ensureDefaultCategories()
        viewModel = newViewModel()
    }

    private fun newViewModel() = BudgetViewModel(
        monthlyBudgetRepository = budgetRepository,
        transactionRepository = transactionRepository,
        categoryRepository = categoryRepository,
        userProfileRepository = FakeUserProfileRepository(clock),
        clock = clock,
    )

    private fun TestScope.collectStates(vm: BudgetViewModel): MutableList<BudgetUiState> {
        val states = mutableListOf<BudgetUiState>()
        backgroundScope.launch { vm.uiState.collect { states.add(it) } }
        return states
    }

    private suspend fun addExpense(amountMinor: Long, date: LocalDate, categoryId: String? = null) {
        transactionRepository.insert(
            Transaction(
                id = java.util.UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                amount = Money.ofMinorUnits(amountMinor),
                currencyCode = "INR",
                categoryId = categoryId,
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
    fun `no budget shows an empty field and no rollover notice`() = runTest {
        val states = collectStates(viewModel)
        advanceUntilIdle()
        val state = states.last()

        assertFalse(state.loading)
        assertEquals("March 2024", state.monthLabel)
        assertEquals("", state.totalBudgetInput)
        assertNull(state.existingTotalBudget)
        assertNull(state.rolloverNotice)
        assertEquals(10, state.categoryBudgets.size) // ten default expense categories
    }

    @Test
    fun `existing budget is prefilled and shows pace status`() = runTest {
        budgetRepository.setBudget(2024, 3, Money.ofMinorUnits(3_000_000))
        addExpense(2_000_000, LocalDate.of(2024, 3, 7), "default-food")
        val vm = newViewModel()
        val states = collectStates(vm)
        advanceUntilIdle()
        val state = states.last()

        // Prefill must round-trip through Money.parse.
        assertEquals(3_000_000L, Money.parse(state.totalBudgetInput, "INR").minorUnits)
        assertEquals(2_000_000L, state.monthExpenses.minorUnits)
        assertEquals(66.67, state.usagePercent!!, 0.001)
        assertEquals(BudgetStatus.ON_TRACK, state.status)
        // Per-category spend context is computed from real rows.
        val food = state.categoryBudgets.first { it.category.id == "default-food" }
        assertEquals(2_000_000L, food.spent.minorUnits)
    }

    @Test
    fun `rollover prefills from the previous month with a notice`() = runTest {
        budgetRepository.setBudget(2024, 2, Money.ofMinorUnits(2_500_000))
        val vm = newViewModel()
        val states = collectStates(vm)
        advanceUntilIdle()

        vm.onPreviousMonth() // February has a budget
        advanceUntilIdle()
        assertEquals(2_500_000L, Money.parse(states.last().totalBudgetInput, "INR").minorUnits)
        assertNull(states.last().rolloverNotice)

        vm.onNextMonth() // March has none
        advanceUntilIdle()
        val state = states.last()
        assertEquals("March 2024", state.monthLabel)
        assertEquals(2_500_000L, Money.parse(state.totalBudgetInput, "INR").minorUnits)
        assertNotNull(state.rolloverNotice)
        assertNull(state.existingTotalBudget) // prefilled, NOT saved yet
    }

    @Test
    fun `save writes the total budget and category limits`() = runTest {
        val states = collectStates(viewModel)
        advanceUntilIdle()

        viewModel.onTotalBudgetChange("30000")
        viewModel.onCategoryBudgetChange("default-food", "5000")
        viewModel.onCategoryBudgetChange("default-travel", "") // no cap
        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(states.last().saved)
        assertEquals(3_000_000L, budgetRepository.getBudget(2024, 3)?.totalLimit?.minorUnits)
        assertEquals(500_000L, categoryRepository.getById("default-food")?.budgetLimit?.minorUnits)
        assertNull(categoryRepository.getById("default-travel")?.budgetLimit)
    }

    @Test
    fun `clearing the total removes the existing budget`() = runTest {
        budgetRepository.setBudget(2024, 3, Money.ofMinorUnits(3_000_000))
        val vm = newViewModel()
        val states = collectStates(vm)
        advanceUntilIdle()

        vm.onTotalBudgetChange("")
        vm.onSave()
        advanceUntilIdle()

        assertNull(budgetRepository.getBudget(2024, 3))
        assertTrue(states.last().saved)
    }

    @Test
    fun `invalid amounts are blocked with inline errors and nothing is saved`() = runTest {
        val states = collectStates(viewModel)
        advanceUntilIdle()

        viewModel.onTotalBudgetChange("abc")
        viewModel.onSave()
        assertNotNull(states.last().totalBudgetError)

        viewModel.onTotalBudgetChange("30000")
        viewModel.onCategoryBudgetChange("default-food", "-50")
        viewModel.onSave()
        advanceUntilIdle()

        val food = states.last().categoryBudgets.first { it.category.id == "default-food" }
        assertNotNull(food.error)
        assertNull(budgetRepository.getBudget(2024, 3)) // save was blocked
        assertNull(categoryRepository.getById("default-food")?.budgetLimit)
    }

    @Test
    fun `editing after save resets the saved notice`() = runTest {
        val states = collectStates(viewModel)
        advanceUntilIdle()
        viewModel.onTotalBudgetChange("30000")
        viewModel.onSave()
        advanceUntilIdle()
        assertTrue(states.last().saved)

        viewModel.onTotalBudgetChange("31000")
        assertFalse(states.last().saved)
    }
}
