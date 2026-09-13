package com.left.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.left.app.core.model.IncomeFrequency
import java.time.Instant
import java.time.LocalDate

/**
 * Room entity for configured income sources (table `income_sources`).
 *
 * Relationship (PRD §11): UserProfile 1 ─── * IncomeSource. Because the local
 * MVP is single-profile, the association is implicit (all rows belong to the
 * singleton profile); no profile FK column exists in the source-of-truth schema
 * (05_DATABASE_API_SPEC.md). Documented in DATABASE.md.
 *
 * [nextDate] is nullable for one-time or irregular income.
 */
@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val frequency: IncomeFrequency,
    @ColumnInfo(name = "next_date") val nextDate: LocalDate?,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
