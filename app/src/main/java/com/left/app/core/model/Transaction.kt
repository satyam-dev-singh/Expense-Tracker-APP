package com.left.app.core.model

import com.left.app.core.utils.Money
import java.time.Instant
import java.time.LocalDate

enum class TransactionType { EXPENSE, INCOME }

enum class TransactionSource { MANUAL, VOICE, IMPORT }

/**
 * Domain model for a single transaction.
 *
 * [amount] is always positive and always in integer minor units (see [Money]).
 * [categoryId] is nullable: null means uncategorized, and the database FK is
 * SET_NULL so historical transactions survive category deletion (PRD §11).
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val amount: Money,
    val currencyCode: String,
    val categoryId: String?,
    val merchant: String?,
    val note: String?,
    val date: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val source: TransactionSource,
    val isRecurring: Boolean,
)
