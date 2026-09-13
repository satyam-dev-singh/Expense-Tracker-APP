package com.left.app.core.data.fake

import com.left.app.core.common.MonthRange
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.database.seed.DefaultCategories
import com.left.app.core.model.Category
import com.left.app.core.model.CategoryType
import com.left.app.core.model.MonthlyBudget
import com.left.app.core.model.MonthlyTotals
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import com.left.app.core.utils.sum
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fakes implementing the repository interfaces (PRD §13 boundary).
 * Unit tests exercise use cases against these fakes — pure JVM, no Android
 * runtime needed. Behavior mirrors the Room implementations (half-open month
 * ranges via MonthRange, Money totals, idempotent default-category seeding).
 */
class FakeTransactionRepository : TransactionRepository {

    private val transactions = MutableStateFlow<List<Transaction>>(emptyList())

    override suspend fun insert(transaction: Transaction) {
        transactions.update { it + transaction }
    }

    override suspend fun update(transaction: Transaction) {
        transactions.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
    }

    override suspend fun delete(id: String) {
        transactions.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun getById(id: String): Transaction? =
        transactions.value.firstOrNull { it.id == id }

    override fun observeById(id: String): Flow<Transaction?> =
        transactions.map { list -> list.firstOrNull { it.id == id } }

    override fun observeAll(): Flow<List<Transaction>> = transactions

    override fun observeByMonth(year: Int, month: Int): Flow<List<Transaction>> {
        val range = MonthRange.of(year, month)
        return transactions.map { list -> list.filter { range.contains(it.date) } }
    }

    override suspend fun getByMonth(year: Int, month: Int): List<Transaction> {
        val range = MonthRange.of(year, month)
        return transactions.value.filter { range.contains(it.date) }
    }

    override fun observeByDateRange(start: LocalDate, endExclusive: LocalDate): Flow<List<Transaction>> =
        transactions.map { list ->
            list.filter { !it.date.isBefore(start) && it.date.isBefore(endExclusive) }
        }

    override fun observeByCategory(categoryId: String): Flow<List<Transaction>> =
        transactions.map { list -> list.filter { it.categoryId == categoryId } }

    override fun search(query: String): Flow<List<Transaction>> =
        transactions.map { list ->
            list.filter {
                it.merchant?.contains(query, ignoreCase = true) == true ||
                    it.note?.contains(query, ignoreCase = true) == true
            }
        }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        transactions.map { list ->
            list.sortedWith(
                compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt },
            ).take(limit)
        }

    override fun observeMonthlyTotals(year: Int, month: Int): Flow<MonthlyTotals> =
        observeByMonth(year, month).map { it.toTotals() }

    override suspend fun getMonthlyTotals(year: Int, month: Int): MonthlyTotals =
        getByMonth(year, month).toTotals()

    private fun List<Transaction>.toTotals(): MonthlyTotals {
        val income = filter { it.type == TransactionType.INCOME }.map { it.amount }.sum()
        val expenses = filter { it.type == TransactionType.EXPENSE }.map { it.amount }.sum()
        return MonthlyTotals(income = income, expenses = expenses)
    }
}

class FakeCategoryRepository(
    private val clock: Clock = Clock.systemUTC(),
) : CategoryRepository {

    private val categories = MutableStateFlow<List<Category>>(emptyList())

    override fun observeActiveCategories(): Flow<List<Category>> =
        categories.map { list -> list.filter { !it.isArchived } }

    override fun observeAllCategories(): Flow<List<Category>> = categories

    override suspend fun getById(id: String): Category? =
        categories.value.firstOrNull { it.id == id }

    override suspend fun exists(id: String): Boolean = categories.value.any { it.id == id }

    override suspend fun createCategory(
        name: String,
        iconKey: String,
        type: CategoryType,
        budgetLimit: Money?,
    ): String {
        val now = Instant.now(clock)
        val category = Category(
            id = UUID.randomUUID().toString(),
            name = name,
            iconKey = iconKey,
            type = type,
            budgetLimit = budgetLimit,
            isDefault = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        categories.update { it + category }
        return category.id
    }

    override suspend fun updateCategory(category: Category) {
        categories.update { list -> list.map { if (it.id == category.id) category else it } }
    }

    override suspend fun archive(id: String) {
        categories.update { list -> list.map { if (it.id == id) it.copy(isArchived = true) else it } }
    }

    override suspend fun unarchive(id: String) {
        categories.update { list -> list.map { if (it.id == id) it.copy(isArchived = false) else it } }
    }

    override suspend fun ensureDefaultCategories() {
        val now = Instant.now(clock)
        categories.update { existing ->
            val missing = DefaultCategories.entities(now)
                .filter { default -> existing.none { it.id == default.id } }
                .map { entity ->
                    Category(
                        id = entity.id,
                        name = entity.name,
                        iconKey = entity.iconKey,
                        type = entity.type,
                        budgetLimit = null,
                        isDefault = entity.isDefault,
                        isArchived = entity.isArchived,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                    )
                }
            existing + missing
        }
    }
}

class FakeMonthlyBudgetRepository(
    private val clock: Clock = Clock.systemUTC(),
) : MonthlyBudgetRepository {

    private val budgets = MutableStateFlow<List<MonthlyBudget>>(emptyList())

    override suspend fun setBudget(year: Int, month: Int, totalLimit: Money): MonthlyBudget {
        MonthRange.of(year, month) // validates 1..12, mirroring the Room repository
        val now = Instant.now(clock)
        val existing = budgets.value.firstOrNull { it.year == year && it.month == month }
        val budget = MonthlyBudget(
            id = existing?.id ?: UUID.randomUUID().toString(),
            year = year,
            month = month,
            totalLimit = totalLimit,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        budgets.update { list -> list.filterNot { it.id == budget.id } + budget }
        return budget
    }

    override suspend fun getBudget(year: Int, month: Int): MonthlyBudget? {
        MonthRange.of(year, month)
        return budgets.value.firstOrNull { it.year == year && it.month == month }
    }

    override fun observeBudget(year: Int, month: Int): Flow<MonthlyBudget?> =
        budgets.map { list -> list.firstOrNull { it.year == year && it.month == month } }

    override fun observeAll(): Flow<List<MonthlyBudget>> = budgets

    override suspend fun deleteBudget(year: Int, month: Int) {
        budgets.update { list -> list.filterNot { it.year == year && it.month == month } }
    }
}
