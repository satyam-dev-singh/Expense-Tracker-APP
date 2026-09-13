package com.left.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room entity for the total budget of one calendar month (table `monthly_budgets`).
 *
 * The unique (year, month) index guarantees at most one budget per month;
 * [month] is 1..12 matching java.time.Month. [totalLimitMinor] is integer
 * minor units (PRD §10).
 */
@Entity(
    tableName = "monthly_budgets",
    indices = [Index(value = ["year", "month"], unique = true)],
)
data class MonthlyBudgetEntity(
    @PrimaryKey val id: String,
    val year: Int,
    val month: Int,
    @ColumnInfo(name = "total_limit_minor") val totalLimitMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
