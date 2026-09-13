package com.left.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.left.app.core.common.MonthRange
import com.left.app.core.database.entity.TransactionEntity
import com.left.app.core.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** Row type for per-type monthly sum queries. */
data class TypeSum(
    val type: TransactionType,
    val totalMinor: Long,
)

/**
 * DAO for transactions (PRD §12). Reactive reads return [Flow] so the UI
 * updates automatically when data changes (Technical Architecture §10).
 *
 * All monthly filtering goes through [MonthRange] (half-open epoch-day range)
 * — never string comparison (PRD §16).
 */
@Dao
abstract class TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(transaction: TransactionEntity)

    @Update
    abstract suspend fun update(transaction: TransactionEntity)

    @Delete
    abstract suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("SELECT * FROM transactions WHERE id = :id")
    abstract suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    abstract fun observeById(id: String): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, created_at DESC")
    abstract fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, created_at DESC")
    abstract suspend fun getAll(): List<TransactionEntity>

    // ---- Date range (half-open [startEpochDay, endExclusiveEpochDay)) ----

    @Query(
        """
        SELECT * FROM transactions
        WHERE transaction_date >= :startEpochDay AND transaction_date < :endExclusiveEpochDay
        ORDER BY transaction_date DESC, created_at DESC
        """,
    )
    abstract fun observeByDateRange(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE transaction_date >= :startEpochDay AND transaction_date < :endExclusiveEpochDay
        ORDER BY transaction_date DESC, created_at DESC
        """,
    )
    abstract suspend fun getByDateRange(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
    ): List<TransactionEntity>

    // ---- Monthly variants (the required getByMonth capability, PRD §12) ----

    fun observeByMonth(year: Int, month: Int): Flow<List<TransactionEntity>> {
        val range = MonthRange.of(year, month)
        return observeByDateRange(range.startEpochDay, range.endExclusiveEpochDay)
    }

    suspend fun getByMonth(year: Int, month: Int): List<TransactionEntity> {
        val range = MonthRange.of(year, month)
        return getByDateRange(range.startEpochDay, range.endExclusiveEpochDay)
    }

    // ---- Category ----

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId ORDER BY transaction_date DESC, created_at DESC")
    abstract fun observeByCategory(categoryId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId ORDER BY transaction_date DESC, created_at DESC")
    abstract suspend fun getByCategory(categoryId: String): List<TransactionEntity>

    // ---- Search ----

    /**
     * Searches merchant and note. [escapedQuery] must already have LIKE
     * wildcards escaped (see TransactionRepository.escapeLike); SQLite LIKE is
     * ASCII case-insensitive by default.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE merchant LIKE '%' || :escapedQuery || '%' ESCAPE '\'
           OR note LIKE '%' || :escapedQuery || '%' ESCAPE '\'
        ORDER BY transaction_date DESC, created_at DESC
        """,
    )
    abstract fun search(escapedQuery: String): Flow<List<TransactionEntity>>

    // ---- Recent ----

    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, created_at DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    // ---- Monthly totals ----

    @Query(
        """
        SELECT type AS type, SUM(amount_minor) AS totalMinor
        FROM transactions
        WHERE transaction_date >= :startEpochDay AND transaction_date < :endExclusiveEpochDay
        GROUP BY type
        """,
    )
    abstract fun observeTotalsByDateRange(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
    ): Flow<List<TypeSum>>

    @Query(
        """
        SELECT type AS type, SUM(amount_minor) AS totalMinor
        FROM transactions
        WHERE transaction_date >= :startEpochDay AND transaction_date < :endExclusiveEpochDay
        GROUP BY type
        """,
    )
    abstract suspend fun getTotalsByDateRange(
        startEpochDay: Long,
        endExclusiveEpochDay: Long,
    ): List<TypeSum>

    fun observeMonthlyTotals(year: Int, month: Int): Flow<List<TypeSum>> {
        val range = MonthRange.of(year, month)
        return observeTotalsByDateRange(range.startEpochDay, range.endExclusiveEpochDay)
    }

    suspend fun getMonthlyTotals(year: Int, month: Int): List<TypeSum> {
        val range = MonthRange.of(year, month)
        return getTotalsByDateRange(range.startEpochDay, range.endExclusiveEpochDay)
    }
}
