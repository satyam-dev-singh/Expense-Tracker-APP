package com.left.app.core.data

import com.left.app.core.database.dao.IncomeSourceDao
import com.left.app.core.database.entity.IncomeSourceEntity
import com.left.app.core.model.IncomeFrequency
import com.left.app.core.model.IncomeSource
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Domain-level access to configured income sources. */
interface IncomeSourceRepository {
    fun observeAll(): Flow<List<IncomeSource>>
    fun observeActive(): Flow<List<IncomeSource>>
    suspend fun getById(id: String): IncomeSource?
    suspend fun create(
        name: String,
        amount: Money,
        frequency: IncomeFrequency,
        nextDate: LocalDate?,
        active: Boolean,
    ): IncomeSource
    suspend fun update(incomeSource: IncomeSource)
    suspend fun setActive(id: String, active: Boolean)
    suspend fun delete(id: String)
}

@Singleton
class RoomIncomeSourceRepository @Inject constructor(
    private val incomeSourceDao: IncomeSourceDao,
    private val clock: Clock,
) : IncomeSourceRepository {

    override fun observeAll(): Flow<List<IncomeSource>> =
        incomeSourceDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<IncomeSource>> =
        incomeSourceDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): IncomeSource? = incomeSourceDao.getById(id)?.toDomain()

    override suspend fun create(
        name: String,
        amount: Money,
        frequency: IncomeFrequency,
        nextDate: LocalDate?,
        active: Boolean,
    ): IncomeSource {
        val now = Instant.now(clock)
        val entity = IncomeSourceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            amountMinor = amount.minorUnits,
            frequency = frequency,
            nextDate = nextDate,
            active = active,
            createdAt = now,
            updatedAt = now,
        )
        incomeSourceDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun update(incomeSource: IncomeSource) {
        incomeSourceDao.upsert(incomeSource.toEntity().copy(updatedAt = Instant.now(clock)))
    }

    override suspend fun setActive(id: String, active: Boolean) {
        incomeSourceDao.setActive(id, active, Instant.now(clock))
    }

    override suspend fun delete(id: String) {
        incomeSourceDao.deleteById(id)
    }

    private fun IncomeSourceEntity.toDomain(): IncomeSource = IncomeSource(
        id = id,
        name = name,
        amount = Money.ofMinorUnits(amountMinor),
        frequency = frequency,
        nextDate = nextDate,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun IncomeSource.toEntity(): IncomeSourceEntity = IncomeSourceEntity(
        id = id,
        name = name,
        amountMinor = amount.minorUnits,
        frequency = frequency,
        nextDate = nextDate,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
