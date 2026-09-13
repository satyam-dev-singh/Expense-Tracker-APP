package com.left.app.core.model

import com.left.app.core.utils.Money
import java.time.Instant
import java.time.LocalDate

enum class BillingCycle { WEEKLY, MONTHLY, QUARTERLY, YEARLY }

/** Domain model for a recurring commitment (PRD FR-07). */
data class Subscription(
    val id: String,
    val name: String,
    val amount: Money,
    val currencyCode: String,
    val categoryId: String?,
    val billingCycle: BillingCycle,
    val nextBillingDate: LocalDate,
    val reminderEnabled: Boolean,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
