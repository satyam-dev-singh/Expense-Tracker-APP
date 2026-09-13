package com.left.app.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.SubscriptionRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.model.BillingCycle
import com.left.app.core.model.Category
import com.left.app.core.model.Subscription
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionType
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import com.left.app.core.utils.sum
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CategoryDistributionRow(val categoryName: String, val amount: Money, val percent: Double)
data class MonthlyTrendRow(val monthLabel: String, val income: Money, val expenses: Money, val moneyLeft: Money)
data class AnalyticsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val monthLabel: String = "",
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val insightSummary: String = "",
    val categoryDistribution: List<CategoryDistributionRow> = emptyList(),
    val trends: List<MonthlyTrendRow> = emptyList(),
    val previousMonthExpenses: Money = Money.ZERO,
    val monthOverMonthDelta: Money = Money.ZERO,
    val monthOverMonthPercent: Double? = null,
    val recurringTotal: Money = Money.ZERO,
    val topCategoryName: String? = null,
)
private data class AnalyticsSource(val month: YearMonth, val transactions: List<Transaction>, val categories: List<Category>, val subscriptions: List<Subscription>, val currencyCode: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    subscriptionRepository: SubscriptionRepository,
    userProfileRepository: UserProfileRepository,
    clock: Clock,
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(MonthRange.current(clock).yearMonth)
    val uiState: StateFlow<AnalyticsUiState> = selectedMonth.flatMapLatest { month ->
        combine(
            transactionRepository.observeByDateRange(month.minusMonths(TREND_MONTHS - 1L).atDay(1), month.plusMonths(1).atDay(1)),
            categoryRepository.observeAllCategories(),
            subscriptionRepository.observeActive(),
            userProfileRepository.observeProfile().map { it?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE },
        ) { transactions, categories, subscriptions, currency ->
            buildState(AnalyticsSource(month, transactions, categories, subscriptions, currency))
        }
    }.catch { error ->
        SafeLogger.e(TAG, "analytics stream failed", error)
        emit(AnalyticsUiState(loading = false, error = "Couldn’t load analytics. Please reopen the app."))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun onPreviousMonth() { selectedMonth.update { it.minusMonths(1) } }
    fun onNextMonth() { selectedMonth.update { it.plusMonths(1) } }

    private fun buildState(source: AnalyticsSource): AnalyticsUiState {
        val month = source.month
        val categoriesById = source.categories.associateBy { it.id }
        val current = source.transactions.inMonth(month)
        val previous = source.transactions.inMonth(month.minusMonths(1))
        val monthExpenses = current.expenses()
        val previousExpenses = previous.expenses()
        val distribution = current.categoryDistribution(categoriesById)
        val recurring = current.filter { it.type == TransactionType.EXPENSE && it.isRecurring }.map { it.amount }.sum() + source.subscriptions.map { it.projectedMonthlyAmount() }.sum()
        val trends = (TREND_MONTHS - 1 downTo 0).map { offset ->
            val trendMonth = month.minusMonths(offset.toLong())
            val txs = source.transactions.inMonth(trendMonth)
            val income = txs.income()
            val expenses = txs.expenses()
            MonthlyTrendRow(trendMonth.format(SHORT_MONTH_FORMATTER), income, expenses, income - expenses)
        }
        val delta = monthExpenses - previousExpenses
        return AnalyticsUiState(false, null, month.format(MONTH_FORMATTER), source.currencyCode, insight(month, distribution, current.income(), monthExpenses, delta, recurring), distribution, trends, previousExpenses, delta, percentChange(previousExpenses, delta), recurring, distribution.firstOrNull()?.categoryName)
    }
    private fun List<Transaction>.inMonth(month: YearMonth): List<Transaction> { val range = MonthRange.of(month); return filter { range.contains(it.date) } }
    private fun List<Transaction>.income(): Money = filter { it.type == TransactionType.INCOME }.map { it.amount }.sum()
    private fun List<Transaction>.expenses(): Money = filter { it.type == TransactionType.EXPENSE }.map { it.amount }.sum()
    private fun List<Transaction>.categoryDistribution(categoriesById: Map<String, Category>): List<CategoryDistributionRow> {
        val expenseRows = filter { it.type == TransactionType.EXPENSE }
        val total = expenseRows.map { it.amount }.sum()
        if (total.isZero) return emptyList()
        return expenseRows.groupBy { tx -> tx.categoryId?.let { categoriesById[it]?.name } ?: UNCATEGORIZED }
            .map { (name, txs) -> val amount = txs.map { it.amount }.sum(); CategoryDistributionRow(name, amount, percentage(amount, total)) }
            .sortedWith(compareByDescending<CategoryDistributionRow> { it.amount.minorUnits }.thenBy { it.categoryName })
    }
    private fun Subscription.projectedMonthlyAmount(): Money = when (billingCycle) { BillingCycle.WEEKLY -> Money.ofMinorUnits(Math.multiplyExact(amount.minorUnits, 4L)); BillingCycle.MONTHLY -> amount; BillingCycle.QUARTERLY -> Money.ofMinorUnits(amount.minorUnits / 3L); BillingCycle.YEARLY -> Money.ofMinorUnits(amount.minorUnits / 12L) }
    private fun percentage(part: Money, total: Money): Double = BigDecimal(part.minorUnits).multiply(BigDecimal(100)).divide(BigDecimal(total.minorUnits), 2, RoundingMode.HALF_UP).toDouble()
    private fun percentChange(previous: Money, delta: Money): Double? = if (previous.isZero) null else BigDecimal(delta.minorUnits).multiply(BigDecimal(100)).divide(BigDecimal(previous.minorUnits), 2, RoundingMode.HALF_UP).toDouble()
    private fun insight(month: YearMonth, distribution: List<CategoryDistributionRow>, income: Money, expenses: Money, delta: Money, recurring: Money): String {
        if (expenses.isZero) return "No spending recorded for ${month.format(MONTH_FORMATTER)} yet. Add a few transactions to see patterns."
        val top = distribution.first()
        val direction = when { delta.isPositive -> "Spending is higher than last month."; delta.isNegative -> "Spending is lower than last month."; else -> "Spending is unchanged from last month." }
        val position = if ((income - expenses).isNegative) "Expenses are above income for this month." else "Income still covers spending this month."
        val recurringText = if (recurring.isZero) "No recurring spend is marked yet." else "Recurring commitments are separated so they do not hide day-to-day spend."
        return "${top.categoryName} leads this month at ${top.percent}%. $direction $position $recurringText"
    }
    private companion object { const val TAG = "Analytics"; const val TREND_MONTHS = 6; const val UNCATEGORIZED = "Uncategorized"; val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy"); val SHORT_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy") }
}
