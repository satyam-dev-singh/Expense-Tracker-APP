package com.left.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.left.app.core.database.entity.SubscriptionEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** DAO for recurring subscriptions (PRD FR-07). Storage only in Phase 1 — UI arrives in Phase 7. */
@Dao
interface SubscriptionDao {

    @Upsert
    suspend fun upsert(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions ORDER BY next_billing_date ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE active = 1 ORDER BY next_billing_date ASC")
    fun observeActive(): Flow<List<SubscriptionEntity>>

    @Query("UPDATE subscriptions SET active = :active, updated_at = :updatedAt WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, updatedAt: Instant)
}
