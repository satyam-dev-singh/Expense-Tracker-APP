package com.left.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.left.app.core.model.CategoryType
import java.time.Instant

/**
 * Room entity for categories (table `categories`).
 *
 * Archiving over deletion: [isArchived] hides a category from pickers while
 * preserving the row, so historical transactions keep a resolvable category_id
 * (PRD §11). [budgetLimitMinor] is the optional per-category monthly budget
 * (PRD FR-06), always integer minor units — never Float/Double (PRD §10).
 *
 * [type] is stored as TEXT via Room's built-in enum support.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"])],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    val type: CategoryType,
    @ColumnInfo(name = "budget_limit_minor") val budgetLimitMinor: Long?,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
