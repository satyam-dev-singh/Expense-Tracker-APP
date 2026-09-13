package com.left.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.left.app.core.database.entity.IncomeSourceEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** DAO for configured income sources. */
@Dao
interface IncomeSourceDao {

    @Upsert
    suspend fun upsert(incomeSource: IncomeSourceEntity)

    @Delete
    suspend fun delete(incomeSource: IncomeSourceEntity)

    @Query("DELETE FROM income_sources WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM income_sources WHERE id = :id")
    suspend fun getById(id: String): IncomeSourceEntity?

    @Query("SELECT * FROM income_sources ORDER BY name ASC")
    fun observeAll(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources WHERE active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<IncomeSourceEntity>>

    @Query("UPDATE income_sources SET active = :active, updated_at = :updatedAt WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, updatedAt: Instant)
}
