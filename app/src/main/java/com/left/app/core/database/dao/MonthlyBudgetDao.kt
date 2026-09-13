package com.left.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.left.app.core.database.entity.MonthlyBudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for monthly budgets. At most one row per (year, month) is enforced by a
 * unique index on the entity.
 */
@Dao
interface MonthlyBudgetDao {

    @Upsert
    suspend fun upsert(budget: MonthlyBudgetEntity)

    @Delete
    suspend fun delete(budget: MonthlyBudgetEntity)

    @Query("SELECT * FROM monthly_budgets WHERE year = :year AND month = :month")
    suspend fun getByMonth(year: Int, month: Int): MonthlyBudgetEntity?

    @Query("SELECT * FROM monthly_budgets WHERE year = :year AND month = :month")
    fun observeByMonth(year: Int, month: Int): Flow<MonthlyBudgetEntity?>

    @Query("SELECT * FROM monthly_budgets ORDER BY year DESC, month DESC")
    fun observeAll(): Flow<List<MonthlyBudgetEntity>>
}
