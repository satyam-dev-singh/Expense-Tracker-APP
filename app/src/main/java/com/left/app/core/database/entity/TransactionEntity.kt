package com.left.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity for transactions (table `transactions`).
 *
 * Relationship (PRD §11): Category 1 ─── * Transaction. The FK uses
 * onDelete = SET_NULL: archiving is the normal way to retire a category, but if
 * a hard delete ever happens, historical transactions survive as uncategorized
 * instead of being destroyed.
 *
 * [amountMinor] is integer minor currency units — never Float/Double (PRD §10).
 * [transactionDate] is stored as an epoch-day INTEGER (see Converters.kt), which
 * makes monthly filtering an exact integer range comparison (PRD §16).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["transaction_date"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: TransactionType,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val merchant: String?,
    val note: String?,
    @ColumnInfo(name = "transaction_date") val transactionDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    val source: TransactionSource,
    @ColumnInfo(name = "is_recurring") val isRecurring: Boolean,
)
