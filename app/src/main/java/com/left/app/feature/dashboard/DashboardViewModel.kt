package com.left.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.model.Category
import com.left.app.core.model.Transaction
import com.left.app.core.model.budgetUsagePercentage
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything the dashboard renders — immutable, replaced atomically (UiState pattern, PRD §20). */
data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val monthLabel: String = "",
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val moneyLeft: Money = Money.ZERO,
    val income: Money = Money.ZERO,
    val expenses: Money = Money.ZERO,
    val hasBudget: Boolean = false,
    /** Budget remaining = budget − expenses. NEVER conflated with actual money left (PRD §15). */
    val budgetRemaining: Money? = null,
    val budgetUsagePercent: Double? = null,
    val recentTransactions: List<Transaction> = emptyList(),
    val categoriesById: Map<String, Category> = emptyMap(),
)

/**
 * S06 Dashboard (PRD FR-02): current month position + budget context + recent
 * activity. Pure stream wiring — all math happens in repositories/use cases;
 * this ViewModel only combines Flows into one immutable state.
 *
 * Month navigation is Phase 4; the dashboard always shows the CURRENT month
 * derived from the injected Clock (timezone-aware).
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    monthlyBudgetRepository: MonthlyBudgetRepository,
    userProfileRepository: UserProfileRepository,
    categoryRepository: CategoryRepository,
    clock: Clock,
) : ViewModel() {

    private val currentMonth = MonthRange.current(clock)

    val uiState: StateFlow<DashboardUiState> = combine(
        transactionRepository.observeMonthlyTotals(currentMonth.year, currentMonth.month),
        monthlyBudgetRepository.observeBudget(currentMonth.year, currentMonth.month),
        transactionRepository.observeRecent(RECENT_COUNT),
        userProfileRepository.observeProfile(),
        categoryRepository.observeActiveCategories(),
    ) { totals, budget, recent, profile, categories ->
        DashboardUiState(
            loading = false,
            monthLabel = currentMonth.yearMonth.format(MONTH_FORMATTER),
            currencyCode = profile?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE,
            moneyLeft = totals.remaining,
            income = totals.income,
            expenses = totals.expenses,
            hasBudget = budget != null,
            budgetRemaining = budget?.let { it.totalLimit - totals.expenses },
            budgetUsagePercent = budget?.let { budgetUsagePercentage(totals.expenses, it.totalLimit) },
            recentTransactions = recent,
            categoriesById = categories.associateBy { it.id },
        )
    }.catch { error ->
        SafeLogger.e(TAG, "dashboard stream failed", error)
        emit(
            DashboardUiState(
                loading = false,
                error = "Couldn’t load your numbers. Please reopen the app.",
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private companion object {
        const val RECENT_COUNT = 5
        const val TAG = "Dashboard"
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
