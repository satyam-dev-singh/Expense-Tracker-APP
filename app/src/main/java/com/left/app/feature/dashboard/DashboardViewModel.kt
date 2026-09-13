package com.left.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.domain.BudgetStatus
import com.left.app.core.domain.dailyAllowance
import com.left.app.core.domain.evaluateBudgetStatus
import com.left.app.core.model.Category
import com.left.app.core.model.Transaction
import com.left.app.core.model.budgetUsagePercentage
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Everything the dashboard renders — immutable, replaced atomically (UiState pattern, PRD §20). */
data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val moneyLeft: Money = Money.ZERO,
    val income: Money = Money.ZERO,
    val expenses: Money = Money.ZERO,
    val hasBudget: Boolean = false,
    /** Budget remaining = budget − expenses. NEVER conflated with actual money left (PRD §15). */
    val budgetRemaining: Money? = null,
    val budgetUsagePercent: Double? = null,
    /** Spending pace for the selected month (null without a budget). */
    val budgetStatus: BudgetStatus? = null,
    /** Safe-to-spend per day for the rest of the CURRENT month (null otherwise). */
    val dailyAllowance: Money? = null,
    val recentTransactions: List<Transaction> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
)

/**
 * S06 Dashboard (PRD FR-02 + FR-06): month position, budget context with pacing
 * status and daily allowance, recent activity. Month navigation (Phase 4) moves
 * the totals/budget window via [MonthRange]; "today"-dependent values
 * (allowance) only exist for the current month. All math lives in repositories
 * and pure domain helpers — this ViewModel only combines Flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    monthlyBudgetRepository: MonthlyBudgetRepository,
    userProfileRepository: UserProfileRepository,
    categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val currentMonth: YearMonth = MonthRange.current(clock).yearMonth
    private val selectedMonth = MutableStateFlow(currentMonth)

    val uiState: StateFlow<DashboardUiState> = selectedMonth.flatMapLatest { month ->
        combine(
            transactionRepository.observeMonthlyTotals(month.year, month.monthValue),
            monthlyBudgetRepository.observeBudget(month.year, month.monthValue),
            transactionRepository.observeRecent(RECENT_COUNT),
            userProfileRepository.observeProfile(),
            categoryRepository.observeActiveCategories(),
        ) { totals, budget, recent, profile, categories ->
            val isCurrentMonth = month == currentMonth
            val usage = budget?.let { budgetUsagePercentage(totals.expenses, it.totalLimit) }
            DashboardUiState(
                loading = false,
                monthLabel = month.format(MONTH_FORMATTER),
                isCurrentMonth = isCurrentMonth,
                currencyCode = profile?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE,
                moneyLeft = totals.remaining,
                income = totals.income,
                expenses = totals.expenses,
                hasBudget = budget != null,
                budgetRemaining = budget?.let { it.totalLimit - totals.expenses },
                budgetUsagePercent = usage,
                budgetStatus = usage?.let(::evaluateBudgetStatus),
                dailyAllowance = if (isCurrentMonth) {
                    dailyAllowance(budget?.totalLimit, totals.expenses, LocalDate.now(clock), month)
                } else {
                    null
                },
                recentTransactions = recent,
                categoriesById = categories.associateBy { it.id },
            )
        }
    }.catch { error ->
        SafeLogger.e(TAG, "dashboard stream failed", error)
        emit(
            DashboardUiState(
                loading = false,
                error = "Couldn’t load your numbers. Please reopen the app.",
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun onPreviousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun onNextMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    private companion object {
        const val RECENT_COUNT = 5
        const val TAG = "Dashboard"
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
