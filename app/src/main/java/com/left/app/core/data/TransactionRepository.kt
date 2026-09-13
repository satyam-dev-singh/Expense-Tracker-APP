package com.left.app.core.data

import com.left.app.core.common.MonthRange
import com.left.app.core.database.dao.TransactionDao
import com.left.app.core.database.dao.TypeSum
import com.left.app.core.database.entity.TransactionEntity
import com.left.app.core.model.MonthlyTotals
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Domain-level access to transactions. Monthly queries accept (year, month) and
 * are resolved through [MonthRange]; date-range queries accept [LocalDate]
 * half-open ranges. Callers never see epoch-day storage details.
 */
interface TransactionRepository {
    suspend fun insert(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: String)
    suspend fun getById(id: String): Transaction?
    fun observeById(id: String): Flow<Transaction?>
    fun observeAll(): Flow<List<Transaction>>
    fun observeByMonth(year: Int, month: Int): Flow<List<Transaction>>
    suspend fun getByMonth(year: Int, month: Int): List<Transaction>
    fun observeByDateRange(start: LocalDate, endExclusive: LocalDate): Flow<List<Transaction>>
    fun observeByCategory(categoryId: String): Flow<List<Transaction>>
    fun search(query: String): Flow<List<Transaction>>
    fun observeRecent(limit: Int): Flow<List<Transaction>>
    fun observeMonthlyTotals(year: Int, month: Int): Flow<MonthlyTotals>
    suspend fun getMonthlyTotals(year: Int, month: Int): MonthlyTotals
}

@Singleton
class RoomTransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) : TransactionRepository {

    override suspend fun insert(transaction: Transaction) {
        transactionDao.insert(transaction.toEntity())
    }

    override suspend fun update(transaction: Transaction) {
        transactionDao.update(transaction.toEntity())
    }

    override suspend fun delete(id: String) {
        transactionDao.deleteById(id)
    }

    override suspend fun getById(id: String): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override fun observeById(id: String): Flow<Transaction?> =
        transactionDao.observeById(id).map { it?.toDomain() }

    override fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByMonth(year: Int, month: Int): Flow<List<Transaction>> =
        transactionDao.observeByMonth(year, month).map { list -> list.map { it.toDomain() } }

    override suspend fun getByMonth(year: Int, month: Int): List<Transaction> =
        transactionDao.getByMonth(year, month).map { it.toDomain() }

    override fun observeByDateRange(start: LocalDate, endExclusive: LocalDate): Flow<List<Transaction>> =
        transactionDao.observeByDateRange(start.toEpochDay(), endExclusive.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    override fun observeByCategory(categoryId: String): Flow<List<Transaction>> =
        transactionDao.observeByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    override fun search(query: String): Flow<List<Transaction>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeAll()
        return transactionDao.search(escapeLike(trimmed)).map { list -> list.map { it.toDomain() } }
    }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        transactionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeMonthlyTotals(year: Int, month: Int): Flow<MonthlyTotals> =
        transactionDao.observeMonthlyTotals(year, month).map { it.toMonthlyTotals() }

    override suspend fun getMonthlyTotals(year: Int, month: Int): MonthlyTotals =
        transactionDao.getMonthlyTotals(year, month).toMonthlyTotals()

    /** Escapes LIKE wildcards so user input is matched literally. */
    private fun escapeLike(raw: String): String = buildString(raw.length) {
        for (c in raw) {
            when (c) {
                '\\' -> append("\\\\")
                '%' -> append("\\%")
                '_' -> append("\\_")
                else -> append(c)
            }
        }
    }

    private fun List<TypeSum>.toMonthlyTotals(): MonthlyTotals {
        val incomeMinor = firstOrNull { it.type == TransactionType.INCOME }?.totalMinor ?: 0L
        val expenseMinor = firstOrNull { it.type == TransactionType.EXPENSE }?.totalMinor ?: 0L
        return MonthlyTotals(
            income = Money.ofMinorUnits(incomeMinor),
            expenses = Money.ofMinorUnits(expenseMinor),
        )
    }

    private fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = id,
        type = type,
        amount = Money.ofMinorUnits(amountMinor),
        currencyCode = currencyCode,
        categoryId = categoryId,
        merchant = merchant,
        note = note,
        date = transactionDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        source = source,
        isRecurring = isRecurring,
    )

    private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        type = type,
        amountMinor = amount.minorUnits,
        currencyCode = currencyCode,
        categoryId = categoryId,
        merchant = merchant,
        note = note,
        transactionDate = date,
        createdAt = createdAt,
        updatedAt = updatedAt,
        source = source,
        isRecurring = isRecurring,
    )
}
