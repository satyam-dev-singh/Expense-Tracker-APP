package com.left.app.feature.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.model.Category
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionType
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** One date section in the grouped list (S10). */
data class TransactionDayGroup(
    val date: LocalDate,
    val label: String,
    val transactions: List<Transaction>,
)

/** Immutable state for the transactions screen. */
data class TransactionsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val typeFilter: TransactionType? = null,
    val categoryFilterId: String? = null,
    val amountMinInput: String = "",
    val amountMaxInput: String = "",
    val monthLabel: String = "",
    val searchingAllTime: Boolean = false,
    val groups: List<TransactionDayGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
)

private data class Filters(
    val type: TransactionType?,
    val categoryId: String?,
    val minInput: String,
    val maxInput: String,
)

/**
 * S10 Transactions (PRD FR-11): month navigation, text search, and
 * type/category/amount-range filters.
 *
 * Search vs. month: with an empty query the list is scoped to the selected
 * month (observeByMonth); typing a query searches ALL time via the DAO's
 * escaped LIKE search. Type/category/amount filters are applied in-memory on
 * top of whichever source is active.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    userProfileRepository: UserProfileRepository,
    clock: Clock,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)
    private val selectedMonth = MutableStateFlow(MonthRange.current(clock).yearMonth)
    private val queryFlow = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val categoryFilter = MutableStateFlow<String?>(null)
    private val minInput = MutableStateFlow("")
    private val maxInput = MutableStateFlow("")

    /** Selected month + debounced query drive the backing query. */
    private val monthQueryFlow: Flow<Pair<YearMonth, String>> =
        combine(selectedMonth, queryFlow.debounce(300), ::Pair)

    private val transactionsFlow: Flow<List<Transaction>> = monthQueryFlow
        .flatMapLatest { (month, query) ->
            if (query.isBlank()) {
                transactionRepository.observeByMonth(month.year, month.monthValue)
            } else {
                transactionRepository.search(query)
            }
        }

    private val filtersFlow: Flow<Filters> =
        combine(typeFilter, categoryFilter, minInput, maxInput, ::Filters)

    private val currencyFlow: Flow<String> = userProfileRepository.observeProfile()
        .map { it?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE }
        .distinctUntilChanged()

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionsFlow,
        filtersFlow,
        categoryRepository.observeActiveCategories(),
        monthQueryFlow,
        currencyFlow,
    ) { transactions, filters, categories, monthQuery, currency ->
        val (month, query) = monthQuery
        val minMoney = filters.minInput.parseFilterAmount(currency)
        val maxMoney = filters.maxInput.parseFilterAmount(currency)

        val filtered = transactions.filter { transaction ->
            (filters.type == null || transaction.type == filters.type) &&
                (filters.categoryId == null || transaction.categoryId == filters.categoryId) &&
                (minMoney == null || transaction.amount >= minMoney) &&
                (maxMoney == null || transaction.amount <= maxMoney)
        }

        TransactionsUiState(
            loading = false,
            query = query,
            typeFilter = filters.type,
            categoryFilterId = filters.categoryId,
            amountMinInput = filters.minInput,
            amountMaxInput = filters.maxInput,
            monthLabel = month.format(MONTH_FORMATTER),
            searchingAllTime = query.isNotBlank(),
            groups = filtered
                .groupBy { it.date }
                .toSortedMap(compareByDescending { it })
                .map { (date, items) ->
                    TransactionDayGroup(date = date, label = dayLabel(date, today), transactions = items)
                },
            categories = categories,
            currencyCode = currency,
        )
    }.catch { error ->
        SafeLogger.e(TAG, "transactions stream failed", error)
        emit(TransactionsUiState(loading = false, error = "Couldn’t load transactions. Please reopen the app."))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    // ---- Events ----

    fun onQueryChange(value: String) {
        queryFlow.value = value
    }

    fun onTypeFilterChange(value: TransactionType?) {
        typeFilter.value = value
    }

    fun onCategoryFilterChange(categoryId: String?) {
        categoryFilter.value = categoryId
    }

    fun onAmountMinChange(value: String) {
        minInput.value = value
    }

    fun onAmountMaxChange(value: String) {
        maxInput.value = value
    }

    fun onPreviousMonth() {
        selectedMonth.update { it.minusMonths(1) }
    }

    fun onNextMonth() {
        selectedMonth.update { it.plusMonths(1) }
    }

    private fun String.parseFilterAmount(currencyCode: String): Money? =
        if (isBlank()) null else runCatching { Money.parse(this, currencyCode) }.getOrNull()

    private companion object {
        const val TAG = "Transactions"
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

        /** Today / Yesterday / short date (S10 grouping labels). */
        fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
        }
    }
}
