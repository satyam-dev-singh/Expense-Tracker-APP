package com.left.app.feature.onboarding

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeIncomeSourceRepository
import com.left.app.core.data.fake.FakeMonthlyBudgetRepository
import com.left.app.core.data.fake.FakeOnboardingPreferences
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.domain.CompleteOnboarding
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Swaps Dispatchers.Main for a test dispatcher so ViewModels run on the JVM. */
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * OnboardingViewModel tests (Phase 2): step flow, per-field validation, skip
 * semantics, completion, and the double-finish guard.
 */
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var events: MutableList<String>
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var budgetRepository: FakeMonthlyBudgetRepository
    private lateinit var incomeSourceRepository: FakeIncomeSourceRepository
    private lateinit var preferences: FakeOnboardingPreferences
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        events = mutableListOf()
        categoryRepository = FakeCategoryRepository(clock, events)
        budgetRepository = FakeMonthlyBudgetRepository(clock, events)
        incomeSourceRepository = FakeIncomeSourceRepository(clock, events)
        preferences = FakeOnboardingPreferences(events)
        // Defaults exist before the flow reaches the categories step.
        runBlocking { categoryRepository.ensureDefaultCategories() }
        viewModel = OnboardingViewModel(
            completeOnboarding = CompleteOnboarding(
                FakeUserProfileRepository(clock, events),
                budgetRepository,
                incomeSourceRepository,
                categoryRepository,
                preferences,
                clock,
            ),
            categoryRepository = categoryRepository,
        )
    }

    @Test
    fun `starts at welcome with INR default and seeded categories`() {
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.step)
        assertEquals("INR", state.currencyCode)
        assertEquals(10, state.categories.size)
        assertFalse(state.completed)
    }

    @Test
    fun `blank name shows an error and stays on welcome`() {
        viewModel.onNext()
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.WELCOME, state.step)
        assertNotNull(state.nameError)
    }

    @Test
    fun `editing the name clears the error`() {
        viewModel.onNext()
        viewModel.onNameChange("S")
        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun `valid name advances to currency`() {
        viewModel.onNameChange("Satyam")
        viewModel.onNext()
        assertEquals(OnboardingStep.CURRENCY, viewModel.uiState.value.step)
    }

    @Test
    fun `invalid 3-letter custom currency blocks the step`() {
        viewModel.onNameChange("Satyam")
        viewModel.onNext()
        viewModel.onCustomCurrencyChange("XYZ")
        viewModel.onNext()
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.CURRENCY, state.step)
        assertNotNull(state.currencyError)
    }

    @Test
    fun `partial custom input keeps the previous valid currency`() {
        viewModel.onNameChange("Satyam")
        viewModel.onNext()
        viewModel.onCustomCurrencyChange("XX") // not yet 3 letters
        assertEquals("INR", viewModel.uiState.value.currencyCode)
        viewModel.onNext()
        assertEquals(OnboardingStep.INCOME, viewModel.uiState.value.step)
    }

    @Test
    fun `back from currency returns to welcome`() {
        viewModel.onNameChange("Satyam")
        viewModel.onNext()
        viewModel.onBack()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.step)
    }

    @Test
    fun `malformed income amount shows an error and stays`() {
        advanceToIncome()
        viewModel.onIncomeAmountChange("abc")
        viewModel.onNext()
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.INCOME, state.step)
        assertNotNull(state.incomeError)
    }

    @Test
    fun `skip income clears the input and moves to budget`() {
        advanceToIncome()
        viewModel.onIncomeAmountChange("50000")
        viewModel.onSkip()
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.BUDGET, state.step)
        assertEquals("", state.incomeAmountInput)
    }

    @Test
    fun `full flow completes and persists through the use case`() = runTest {
        advanceToIncome()
        viewModel.onIncomeAmountChange("50000")
        viewModel.onNext() // -> BUDGET
        viewModel.onBudgetChange("30000")
        viewModel.onNext() // -> CATEGORIES
        viewModel.onCategoryToggle("default-travel")
        viewModel.onNext() // finish

        val state = viewModel.uiState.value
        assertTrue(state.completed)
        assertFalse(state.saving)

        assertTrue(preferences.completed)
        assertEquals(3_000_000L, budgetRepository.getBudget(2024, 3)?.totalLimit?.minorUnits)
        assertEquals(5_000_000L, incomeSourceRepository.observeAll().first().single().amount.minorUnits)
        assertTrue(categoryRepository.getById("default-travel")!!.isArchived)
        assertEquals("completed-flag", events.last())
    }

    @Test
    fun `finish is guarded against double invocation`() = runTest {
        advanceToIncome()
        viewModel.onSkip() // -> BUDGET
        viewModel.onSkip() // -> CATEGORIES
        viewModel.onNext() // finish
        viewModel.onNext() // must be a no-op

        assertTrue(viewModel.uiState.value.completed)
        assertEquals(1, events.count { it == "profile" })
    }

    /** Drives the flow to the INCOME step with valid name and default currency. */
    private fun advanceToIncome() {
        viewModel.onNameChange("Satyam")
        viewModel.onNext() // -> CURRENCY
        viewModel.onNext() // -> INCOME (INR default is valid)
        assertEquals(OnboardingStep.INCOME, viewModel.uiState.value.step)
    }
}
