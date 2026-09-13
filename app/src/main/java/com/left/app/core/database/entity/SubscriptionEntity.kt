package com.left.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.left.app.core.model.BillingCycle
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity for recurring subscriptions (table `subscriptions`, PRD FR-07).
 *
 * Relationship (PRD §11): Category 1 ─── * Subscription, with the same
 * SET_NULL delete policy as transactions.
 */
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["category_id"])],
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "billing_cycle") val billingCycle: BillingCycle,
    @ColumnInfo(name = "next_billing_date") val nextBillingDate: LocalDate,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
