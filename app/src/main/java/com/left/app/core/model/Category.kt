package com.left.app.core.model

import com.left.app.core.utils.Money
import java.time.Instant

enum class CategoryType { EXPENSE, INCOME, BOTH }

/**
 * Domain model for a transaction category.
 *
 * Categories are archived, never hard-deleted in normal flows, so historical
 * transactions remain understandable (PRD §11). [budgetLimit] supports the
 * per-category budgets of PRD FR-06; null means no cap.
 */
data class Category(
    val id: String,
    val name: String,
    val iconKey: String,
    val type: CategoryType,
    val budgetLimit: Money?,
    val isDefault: Boolean,
    val isArchived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
