package com.left.app.core.data

import com.left.app.core.database.dao.SubscriptionDao
import com.left.app.core.database.entity.SubscriptionEntity
import com.left.app.core.model.BillingCycle
import com.left.app.core.model.Subscription
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Domain-level access to recurring subscriptions (PRD FR-07). UI ships in Phase 7. */
interface SubscriptionRepository {
    fun observeAll(): Flow<List<Subscription>>
    fun observeActive(): Flow<List<Subscription>>
    suspend fun getById(id: String): Subscription?
    suspend fun create(
        name: String,
        amount: Money,
        currencyCode: String,
        categoryId: String?,
        billingCycle: BillingCycle,
        nextBillingDate: LocalDate,
        reminderEnabled: Boolean,
        active: Boolean,
    ): Subscription
    suspend fun update(subscription: Subscription)
    suspend fun setActive(id: String, active: Boolean)
    suspend fun delete(id: String)
}

@Singleton
class RoomSubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val clock: Clock,
) : SubscriptionRepository {

    override fun observeAll(): Flow<List<Subscription>> =
        subscriptionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Subscription>> =
        subscriptionDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Subscription? = subscriptionDao.getById(id)?.toDomain()

    override suspend fun create(
        name: String,
        amount: Money,
        currencyCode: String,
        categoryId: String?,
        billingCycle: BillingCycle,
        nextBillingDate: LocalDate,
        reminderEnabled: Boolean,
        active: Boolean,
    ): Subscription {
        val now = Instant.now(clock)
        val entity = SubscriptionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            amountMinor = amount.minorUnits,
            currencyCode = currencyCode,
            categoryId = categoryId,
            billingCycle = billingCycle,
            nextBillingDate = nextBillingDate,
            reminderEnabled = reminderEnabled,
            active = active,
            createdAt = now,
            updatedAt = now,
        )
        subscriptionDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun update(subscription: Subscription) {
        subscriptionDao.upsert(subscription.toEntity().copy(updatedAt = Instant.now(clock)))
    }

    override suspend fun setActive(id: String, active: Boolean) {
        subscriptionDao.setActive(id, active, Instant.now(clock))
    }

    override suspend fun delete(id: String) {
        subscriptionDao.deleteById(id)
    }

    private fun SubscriptionEntity.toDomain(): Subscription = Subscription(
        id = id,
        name = name,
        amount = Money.ofMinorUnits(amountMinor),
        currencyCode = currencyCode,
        categoryId = categoryId,
        billingCycle = billingCycle,
        nextBillingDate = nextBillingDate,
        reminderEnabled = reminderEnabled,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
        id = id,
        name = name,
        amountMinor = amount.minorUnits,
        currencyCode = currencyCode,
        categoryId = categoryId,
        billingCycle = billingCycle,
        nextBillingDate = nextBillingDate,
        reminderEnabled = reminderEnabled,
        active = active,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
