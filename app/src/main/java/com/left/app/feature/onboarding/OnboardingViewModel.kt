package com.left.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.data.CategoryRepository
import com.left.app.core.domain.CompleteOnboarding
import com.left.app.core.domain.OnboardingIncome
import com.left.app.core.domain.OnboardingResult
import com.left.app.core.model.Category
import com.left.app.core.model.IncomeFrequency
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import com.left.app.core.utils.MoneyParseException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ordered onboarding steps (Implementation Plan Phase 2; UX/UI Spec S02–S05). */
enum class OnboardingStep { WELCOME, CURRENCY, INCOME, BUDGET, CATEGORIES }

/** Immutable UI state for the onboarding flow. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val nameError: String? = null,
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val customCurrencyCode: String = "",
    val currencyError: String? = null,
    val incomeName: String = "Salary",
    val incomeAmountInput: String = "",
    val incomeFrequency: IncomeFrequency = IncomeFrequency.MONTHLY,
    val incomeError: String? = null,
    val budgetInput: String = "",
    val budgetError: String? = null,
    val categories: List<Category> = emptyList(),
    val deselectedCategoryIds: Set<String> = emptySet(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val completed: Boolean = false,
)

/**
 * Onboarding state machine. Owns the step order, per-field validation, and skip
 * semantics (PRD FR-01: optional configuration can be skipped). All persistence
 * goes through [CompleteOnboarding] — no business logic in composables.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboarding,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.observeActiveCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    // ---- Field updates (editing a field clears its error) ----

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = null) }
    }

    fun onCurrencySelected(code: String) {
        _uiState.update { it.copy(currencyCode = code, customCurrencyCode = "", currencyError = null) }
    }

    fun onCustomCurrencyChange(value: String) {
        val clean = value.filter { it.isLetter() }.uppercase().take(3)
        _uiState.update {
            it.copy(
                customCurrencyCode = clean,
                currencyCode = if (clean.length == 3) clean else it.currencyCode,
                currencyError = null,
            )
        }
    }

    fun onIncomeNameChange(value: String) {
        _uiState.update { it.copy(incomeName = value) }
    }

    fun onIncomeAmountChange(value: String) {
        _uiState.update { it.copy(incomeAmountInput = value, incomeError = null) }
    }

    fun onIncomeFrequencyChange(value: IncomeFrequency) {
        _uiState.update { it.copy(incomeFrequency = value) }
    }

    fun onBudgetChange(value: String) {
        _uiState.update { it.copy(budgetInput = value, budgetError = null) }
    }

    fun onCategoryToggle(categoryId: String) {
        _uiState.update {
            it.copy(
                deselectedCategoryIds = if (categoryId in it.deselectedCategoryIds) {
                    it.deselectedCategoryIds - categoryId
                } else {
                    it.deselectedCategoryIds + categoryId
                },
            )
        }
    }

    // ---- Step navigation ----

    fun onBack() {
        _uiState.update { state ->
            val previous = state.step.ordinal - 1
            if (previous < 0) state else state.copy(step = OnboardingStep.entries[previous])
        }
    }

    /** Skips an optional step, clearing its input so nothing is persisted for it. */
    fun onSkip() {
        _uiState.update { state ->
            when (state.step) {
                OnboardingStep.INCOME -> state.copy(
                    step = OnboardingStep.BUDGET,
                    incomeAmountInput = "",
                    incomeError = null,
                )
                OnboardingStep.BUDGET -> state.copy(
                    step = OnboardingStep.CATEGORIES,
                    budgetInput = "",
                    budgetError = null,
                )
                else -> state
            }
        }
    }

    fun onNext() {
        val state = _uiState.value
        if (state.saving || state.completed) return
        when (state.step) {
            OnboardingStep.WELCOME -> {
                if (state.name.isBlank()) {
                    _uiState.update { it.copy(nameError = "Please enter your name") }
                } else {
                    _uiState.update { it.copy(step = OnboardingStep.CURRENCY) }
                }
            }
            OnboardingStep.CURRENCY -> {
                if (!CurrencyUtils.isValidCurrencyCode(state.currencyCode)) {
                    _uiState.update { it.copy(currencyError = "Enter a valid 3-letter currency code") }
                } else {
                    _uiState.update { it.copy(step = OnboardingStep.INCOME) }
                }
            }
            OnboardingStep.INCOME -> {
                if (amountIsValid(state.incomeAmountInput, state.currencyCode) { error ->
                    _uiState.update { it.copy(incomeError = error) }
                }) {
                    _uiState.update { it.copy(step = OnboardingStep.BUDGET) }
                }
            }
            OnboardingStep.BUDGET -> {
                if (amountIsValid(state.budgetInput, state.currencyCode) { error ->
                    _uiState.update { it.copy(budgetError = error) }
                }) {
                    _uiState.update { it.copy(step = OnboardingStep.CATEGORIES) }
                }
            }
            OnboardingStep.CATEGORIES -> finish()
        }
    }

    /** Blank input is valid (the step is being skipped); non-blank must parse positive. */
    private fun amountIsValid(input: String, currencyCode: String, onError: (String) -> Unit): Boolean {
        if (input.isBlank()) return true
        val parsed = try {
            Money.parse(input, currencyCode)
        } catch (e: MoneyParseException) {
            onError("Enter a valid amount")
            return false
        }
        if (!parsed.isPositive) {
            onError("Amount must be greater than zero")
            return false
        }
        return true
    }

    private fun finish() {
        val snapshot = _uiState.value
        if (snapshot.saving || snapshot.completed) return
        _uiState.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                completeOnboarding(buildResult(snapshot))
                _uiState.update { it.copy(saving = false, completed = true) }
            } catch (e: Exception) {
                // Technical detail goes to the debug log; users see a safe message (PRD §20).
                SafeLogger.w(TAG, "onboarding completion failed", e)
                _uiState.update {
                    it.copy(
                        saving = false,
                        saveError = "Something went wrong while saving. Nothing was saved yet — please try again.",
                    )
                }
            }
        }
    }

    private fun buildResult(state: OnboardingUiState): OnboardingResult {
        val income = if (state.incomeAmountInput.isBlank()) {
            null
        } else {
            OnboardingIncome(
                name = state.incomeName.ifBlank { "Income" },
                amount = Money.parse(state.incomeAmountInput, state.currencyCode),
                frequency = state.incomeFrequency,
            )
        }
        val budget = if (state.budgetInput.isBlank()) {
            null
        } else {
            Money.parse(state.budgetInput, state.currencyCode)
        }
        return OnboardingResult(
            name = state.name,
            currencyCode = state.currencyCode,
            locale = Locale.getDefault().toLanguageTag(),
            income = income,
            monthlyBudget = budget,
            archiveCategoryIds = state.deselectedCategoryIds,
        )
    }

    private companion object {
        const val TAG = "Onboarding"
    }
}
