package com.left.app.core.data.fake

import com.left.app.core.data.SubscriptionRepository
import com.left.app.core.model.BillingCycle
import com.left.app.core.model.Subscription
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSubscriptionRepository(private val clock: Clock = Clock.systemUTC()) : SubscriptionRepository {
    private val subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    override fun observeAll(): Flow<List<Subscription>> = subscriptions
    override fun observeActive(): Flow<List<Subscription>> = subscriptions.map { list -> list.filter { it.active } }
    override suspend fun getById(id: String): Subscription? = subscriptions.value.firstOrNull { it.id == id }
    override suspend fun create(name: String, amount: Money, currencyCode: String, categoryId: String?, billingCycle: BillingCycle, nextBillingDate: LocalDate, reminderEnabled: Boolean, active: Boolean): Subscription { val now = Instant.now(clock); val subscription = Subscription(UUID.randomUUID().toString(), name, amount, currencyCode, categoryId, billingCycle, nextBillingDate, reminderEnabled, active, now, now); subscriptions.update { it + subscription }; return subscription }
    override suspend fun update(subscription: Subscription) { subscriptions.update { list -> list.map { if (it.id == subscription.id) subscription else it } } }
    override suspend fun setActive(id: String, active: Boolean) { subscriptions.update { list -> list.map { if (it.id == id) it.copy(active = active) else it } } }
    override suspend fun delete(id: String) { subscriptions.update { list -> list.filterNot { it.id == id } } }
}
