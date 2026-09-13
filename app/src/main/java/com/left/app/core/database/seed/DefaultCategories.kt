package com.left.app.core.database.seed

import com.left.app.core.database.entity.CategoryEntity
import com.left.app.core.model.CategoryType
import java.time.Instant

/** Definition of one seeded default category. */
data class DefaultCategoryDefinition(
    val id: String,
    val name: String,
    val iconKey: String,
)

/**
 * Default categories (PRD §17 / FR-05).
 *
 * IDs are DETERMINISTIC ("default-<slug>") and insertion uses
 * INSERT OR IGNORE, which together make seeding idempotent: running it on
 * every app start never creates duplicates.
 *
 * All defaults are EXPENSE categories — the PRD default list is spending-focused;
 * income transactions may remain uncategorized until Phase 2 onboarding.
 * Documented in DATABASE.md.
 */
object DefaultCategories {

    private const val ID_PREFIX = "default-"

    val definitions: List<DefaultCategoryDefinition> = listOf(
        DefaultCategoryDefinition(ID_PREFIX + "food", "Food", "restaurant"),
        DefaultCategoryDefinition(ID_PREFIX + "transport", "Transport", "directions_car"),
        DefaultCategoryDefinition(ID_PREFIX + "shopping", "Shopping", "shopping_bag"),
        DefaultCategoryDefinition(ID_PREFIX + "bills", "Bills", "receipt_long"),
        DefaultCategoryDefinition(ID_PREFIX + "entertainment", "Entertainment", "movie"),
        DefaultCategoryDefinition(ID_PREFIX + "health", "Health", "health_and_safety"),
        DefaultCategoryDefinition(ID_PREFIX + "education", "Education", "school"),
        DefaultCategoryDefinition(ID_PREFIX + "travel", "Travel", "flight"),
        DefaultCategoryDefinition(ID_PREFIX + "personal", "Personal", "person"),
        DefaultCategoryDefinition(ID_PREFIX + "other", "Other", "category"),
    )

    /** Fresh entity rows stamped with [now]; only rows with new IDs will be inserted. */
    fun entities(now: Instant): List<CategoryEntity> = definitions.map { definition ->
        CategoryEntity(
            id = definition.id,
            name = definition.name,
            iconKey = definition.iconKey,
            type = CategoryType.EXPENSE,
            budgetLimitMinor = null,
            isDefault = true,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
