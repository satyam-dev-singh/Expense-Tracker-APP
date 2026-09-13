package com.left.app.core.data

import com.left.app.core.common.MonthRange
import com.left.app.core.database.dao.MonthlyBudgetDao
import com.left.app.core.database.entity.MonthlyBudgetEntity
import com.left.app.core.model.MonthlyBudget
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Domain-level access to monthly budgets (PRD FR-06). */
interface MonthlyBudgetRepository {
    /** Creates or updates the budget for ([year], [month]); one budget per month is DB-enforced. */
    suspend fun setBudget(year: Int, month: Int, totalLimit: Money): MonthlyBudget
    suspend fun getBudget(year: Int, month: Int): MonthlyBudget?
    fun observeBudget(year: Int, month: Int): Flow<MonthlyBudget?>
    fun observeAll(): Flow<List<MonthlyBudget>>
    suspend fun deleteBudget(year: Int, month: Int)
}

@Singleton
class RoomMonthlyBudgetRepository @Inject constructor(
    private val monthlyBudgetDao: MonthlyBudgetDao,
    private val clock: Clock,
) : MonthlyBudgetRepository {

    override suspend fun setBudget(year: Int, month: Int, totalLimit: Money): MonthlyBudget {
        MonthRange.of(year, month) // validates month is 1..12
        val now = Instant.now(clock)
        val existing = monthlyBudgetDao.getByMonth(year, month)
        val entity = MonthlyBudgetEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            year = year,
            month = month,
            totalLimitMinor = totalLimit.minorUnits,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        monthlyBudgetDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun getBudget(year: Int, month: Int): MonthlyBudget? {
        MonthRange.of(year, month)
        return monthlyBudgetDao.getByMonth(year, month)?.toDomain()
    }

    override fun observeBudget(year: Int, month: Int): Flow<MonthlyBudget?> {
        MonthRange.of(year, month)
        return monthlyBudgetDao.observeByMonth(year, month).map { it?.toDomain() }
    }

    override fun observeAll(): Flow<List<MonthlyBudget>> =
        monthlyBudgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun deleteBudget(year: Int, month: Int) {
        monthlyBudgetDao.getByMonth(year, month)?.let { monthlyBudgetDao.delete(it) }
    }

    private fun MonthlyBudgetEntity.toDomain(): MonthlyBudget = MonthlyBudget(
        id = id,
        year = year,
        month = month,
        totalLimit = Money.ofMinorUnits(totalLimitMinor),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
