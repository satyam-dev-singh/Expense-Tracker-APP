package com.left.app.core.domain

import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.SubscriptionRepository
import com.left.app.core.model.BillingCycle
import com.left.app.core.model.Subscription
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import java.time.LocalDate
import javax.inject.Inject

class SubscriptionValidationException(message: String) : IllegalArgumentException(message)

data class NewSubscription(val name: String, val amount: Money, val currencyCode: String, val categoryId: String?, val billingCycle: BillingCycle, val nextBillingDate: LocalDate, val reminderEnabled: Boolean, val active: Boolean = true)

class AddSubscription @Inject constructor(private val repository: SubscriptionRepository, private val categories: CategoryRepository) {
    suspend operator fun invoke(input: NewSubscription): Subscription { validate(input); return repository.create(input.name.trim(), input.amount, input.currencyCode, input.categoryId, input.billingCycle, input.nextBillingDate, input.reminderEnabled, input.active) }
    private suspend fun validate(input: NewSubscription) {
        if (input.name.isBlank()) throw SubscriptionValidationException("Enter a subscription name")
        if (!input.amount.isPositive) throw SubscriptionValidationException("Amount must be greater than zero")
        if (!CurrencyUtils.isValidCurrencyCode(input.currencyCode) || input.currencyCode != input.currencyCode.uppercase()) throw SubscriptionValidationException("Use a valid uppercase currency code")
        if (input.categoryId != null && !categories.exists(input.categoryId)) throw SubscriptionValidationException("Choose an existing category")
    }
}
class UpdateSubscription @Inject constructor(private val repository: SubscriptionRepository, private val categories: CategoryRepository) {
    suspend operator fun invoke(subscription: Subscription) {
        if (subscription.name.isBlank()) throw SubscriptionValidationException("Enter a subscription name")
        if (!subscription.amount.isPositive) throw SubscriptionValidationException("Amount must be greater than zero")
        if (!CurrencyUtils.isValidCurrencyCode(subscription.currencyCode) || subscription.currencyCode != subscription.currencyCode.uppercase()) throw SubscriptionValidationException("Use a valid uppercase currency code")
        if (subscription.categoryId != null && !categories.exists(subscription.categoryId)) throw SubscriptionValidationException("Choose an existing category")
        repository.update(subscription.copy(name = subscription.name.trim()))
    }
}
class DeleteSubscription @Inject constructor(private val repository: SubscriptionRepository) { suspend operator fun invoke(id: String) = repository.delete(id) }
class ToggleSubscriptionActive @Inject constructor(private val repository: SubscriptionRepository) { suspend operator fun invoke(id: String, active: Boolean) = repository.setActive(id, active) }
fun nextBillingDate(from: LocalDate, cycle: BillingCycle): LocalDate = when (cycle) { BillingCycle.WEEKLY -> from.plusWeeks(1); BillingCycle.MONTHLY -> from.plusMonths(1); BillingCycle.QUARTERLY -> from.plusMonths(3); BillingCycle.YEARLY -> from.plusYears(1) }
fun projectedMonthlyAmount(amount: Money, cycle: BillingCycle): Money = when (cycle) { BillingCycle.WEEKLY -> Money.ofMinorUnits(Math.multiplyExact(amount.minorUnits, 4L)); BillingCycle.MONTHLY -> amount; BillingCycle.QUARTERLY -> Money.ofMinorUnits(amount.minorUnits / 3L); BillingCycle.YEARLY -> Money.ofMinorUnits(amount.minorUnits / 12L) }
