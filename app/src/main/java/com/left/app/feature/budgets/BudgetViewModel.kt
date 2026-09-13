package com.left.app.feature.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.domain.BudgetStatus
import com.left.app.core.domain.evaluateBudgetStatus
import com.left.app.core.model.Category
import com.left.app.core.model.CategoryType
import com.left.app.core.model.TransactionType
import com.left.app.core.model.budgetUsagePercentage
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import com.left.app.core.utils.MoneyParseException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One editable category-limit row. */
data class CategoryBudgetInput(
    val category: Category,
    val spent: Money,
    val input: String,
    val error: String?,
)

/** Immutable state for the budget management screen (S14, PRD FR-06). */
data class BudgetUiState(
    val loading: Boolean = true,
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val totalBudgetInput: String = "",
    val existingTotalBudget: Money? = null,
    val totalBudgetError: String? = null,
    val monthExpenses: Money = Money.ZERO,
    val usagePercent: Double? = null,
    val status: BudgetStatus? = null,
    val categoryBudgets: List<CategoryBudgetInput> = emptyList(),
    val rolloverNotice: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveError: String? = null,
)

private data class MonthData(
    val month: YearMonth,
    val totalBudget: Money?,
    val expenses: Money,
    val spentByCategory: Map<String, Money>,
)

private data class FormState(
    val total: String,
    val categoryInputs: Map<String, String>,
    val rolloverNotice: String?,
)

private data class SaveState(
    val saving: Boolean,
    val saved: Boolean,
    val saveError: String?,
    val totalError: String?,
    val categoryErrors: Map<String, String>,
)

/**
 * S14 Budgets (PRD FR-06, Phase 4): total monthly budget + per-category limits.
 *
 *  - Month navigation: switching months reloads that month's budget. Rollover:
 *    when the selected month has no budget but the previous one does, the field
 *    is prefilled from it (with a notice) — never silently saved.
 *  - Form inputs are imperative state; streams only supply spent/status context,
 *    so edits are never clobbered by re-emissions.
 *  - Save: blank total removes the month's budget; per-category blank clears
 *    that limit. All amounts parse through [Money] (PRD §10/§18).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val monthlyBudgetRepository: MonthlyBudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    userProfileRepository: UserProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val currentMonth: YearMonth = MonthRange.current(clock).yearMonth
    private val selectedMonth = MutableStateFlow(currentMonth)

    private val totalInput = MutableStateFlow("")
    private val categoryInputs = MutableStateFlow<Map<String, String>>(emptyMap())
    private val rolloverNotice = MutableStateFlow<String?>(null)
    private val savingFlag = MutableStateFlow(false)
    private val savedFlag = MutableStateFlow(false)
    private val saveErrorFlow = MutableStateFlow<String?>(null)
    private val totalError = MutableStateFlow<String?>(null)
    private val categoryErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    private var cachedCategories: List<Category> = emptyList()
    private var cachedCurrency: String = CurrencyUtils.DEFAULT_CURRENCY_CODE

    private val monthData = selectedMonth.flatMapLatest { month ->
        combine(
            monthlyBudgetRepository.observeBudget(month.year, month.monthValue),
            transactionRepository.observeMonthlyTotals(month.year, month.monthValue),
            transactionRepository.observeByMonth(month.year, month.monthValue),
        ) { budget, totals, transactions ->
            MonthData(
                month = month,
                totalBudget = budget?.totalLimit,
                expenses = totals.expenses,
                spentByCategory = transactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .filter { it.categoryId != null }
                    .groupBy { it.categoryId!! }
                    .mapValues { (_, txs) -> txs.map { it.amount }.reduceOrNull { acc, m -> acc + m } ?: Money.ZERO },
            )
        }
    }

    private val currencyFlow = userProfileRepository.observeProfile()
        .map { it?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE }
        .distinctUntilChanged()

    private val formFlow = combine(totalInput, categoryInputs, rolloverNotice, ::FormState)
    private val saveFlow = combine(
        savingFlag, savedFlag, saveErrorFlow, totalError, categoryErrors, ::SaveState,
    )

    val uiState: StateFlow<BudgetUiState> = combine(
        monthData,
        categoryRepository.observeActiveCategories(),
        currencyFlow,
        formFlow,
        saveFlow,
    ) { data, categories, currency, form, save ->
        val usage = data.totalBudget?.let { budgetUsagePercentage(data.expenses, it) }
        BudgetUiState(
            loading = false,
            monthLabel = data.month.format(MONTH_FORMATTER),
            isCurrentMonth = data.month == currentMonth,
            currencyCode = currency,
            totalBudgetInput = form.total,
            existingTotalBudget = data.totalBudget,
            totalBudgetError = save.totalError,
            monthExpenses = data.expenses,
            usagePercent = usage,
            status = usage?.let(::evaluateBudgetStatus),
            categoryBudgets = categories
                .filter { it.type != CategoryType.INCOME }
                .map { category ->
                    CategoryBudgetInput(
                        category = category,
                        spent = data.spentByCategory[category.id] ?: Money.ZERO,
                        input = form.categoryInputs[category.id].orEmpty(),
                        error = save.categoryErrors[category.id],
                    )
                },
            rolloverNotice = form.rolloverNotice,
            saving = save.saving,
            saved = save.saved,
            saveError = save.saveError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    init {
        viewModelScope.launch {
            categoryRepository.observeActiveCategories().collect { cachedCategories = it }
        }
        viewModelScope.launch {
            currencyFlow.collect { cachedCurrency = it }
        }
        viewModelScope.launch { preloadMonth(selectedMonth.value) }
    }

    // ---- Form events (any edit clears errors and the saved notice) ----

    fun onTotalBudgetChange(value: String) {
        totalInput.value = value
        totalError.value = null
        savedFlag.value = false
    }

    fun onCategoryBudgetChange(categoryId: String, value: String) {
        categoryInputs.value = categoryInputs.value + (categoryId to value)
        categoryErrors.value = categoryErrors.value - categoryId
        savedFlag.value = false
    }

    fun onPreviousMonth() = navigateMonth { it.minusMonths(1) }

    fun onNextMonth() = navigateMonth { it.plusMonths(1) }

    private fun navigateMonth(transform: (YearMonth) -> YearMonth) {
        val target = transform(selectedMonth.value)
        selectedMonth.value = target
        savedFlag.value = false
        totalError.value = null
        categoryErrors.value = emptyMap()
        viewModelScope.launch { preloadMonth(target) }
    }

    /** Prefills inputs from the selected month; falls back to the previous month's budget (rollover). */
    private suspend fun preloadMonth(month: YearMonth) {
        val existing = monthlyBudgetRepository.getBudget(month.year, month.monthValue)
        if (existing != null) {
            totalInput.value = existing.totalLimit.format(cachedCurrency)
            rolloverNotice.value = null
        } else {
            val previousMonth = month.minusMonths(1)
            val previous = monthlyBudgetRepository.getBudget(previousMonth.year, previousMonth.monthValue)
            if (previous != null) {
                totalInput.value = previous.totalLimit.format(cachedCurrency)
                rolloverNotice.value =
                    "Prefilled from ${previousMonth.format(MONTH_FORMATTER)}’s budget — adjust or save to apply."
            } else {
                totalInput.value = ""
                rolloverNotice.value = null
            }
        }
        categoryInputs.value = cachedCategories.associate { category ->
            category.id to (category.budgetLimit?.format(cachedCurrency) ?: "")
        }
    }

    // ---- Save ----

    fun onSave() {
        val month = selectedMonth.value
        if (savingFlag.value) return

        val totalRaw = totalInput.value.trim()
        val parsedTotal: Money? = if (totalRaw.isEmpty()) {
            null // clearing the field removes the month's budget
        } else {
            val parsed = try {
                Money.parse(totalRaw, cachedCurrency)
            } catch (e: MoneyParseException) {
                totalError.value = "Enter a valid amount"
                return
            }
            if (!parsed.isPositive) {
                totalError.value = "Amount must be greater than zero"
                return
            }
            parsed
        }

        val errors = mutableMapOf<String, String>()
        val parsedLimits = mutableMapOf<String, Money?>()
        cachedCategories.forEach { category ->
            val raw = categoryInputs.value[category.id].orEmpty().trim()
            if (raw.isEmpty()) {
                parsedLimits[category.id] = null
            } else {
                val parsed = try {
                    Money.parse(raw, cachedCurrency)
                } catch (e: MoneyParseException) {
                    errors[category.id] = "Enter a valid amount"
                    null
                }
                if (parsed != null && !parsed.isPositive) {
                    errors[category.id] = "Amount must be greater than zero"
                }
                parsedLimits[category.id] = parsed
            }
        }
        if (errors.isNotEmpty()) {
            categoryErrors.value = errors
            return
        }

        savingFlag.value = true
        saveErrorFlow.value = null
        viewModelScope.launch {
            try {
                val existing = monthlyBudgetRepository.getBudget(month.year, month.monthValue)
                if (parsedTotal == null) {
                    if (existing != null) {
                        monthlyBudgetRepository.deleteBudget(month.year, month.monthValue)
                    }
                } else {
                    monthlyBudgetRepository.setBudget(month.year, month.monthValue, parsedTotal)
                }
                cachedCategories.forEach { category ->
                    val newLimit = parsedLimits[category.id]
                    if (newLimit != category.budgetLimit) {
                        categoryRepository.updateCategory(category.copy(budgetLimit = newLimit))
                    }
                }
                savingFlag.value = false
                savedFlag.value = true
                rolloverNotice.value = null
            } catch (e: Exception) {
                SafeLogger.w(TAG, "budget save failed", e)
                savingFlag.value = false
                saveErrorFlow.value = "Couldn’t save the budget. Please try again."
            }
        }
    }

    private companion object {
        const val TAG = "Budget"
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
