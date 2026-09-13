package com.left.app.core.database.seed

import com.left.app.core.model.CategoryType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Default category tests (PRD §17/FR-05): the exact PRD list is seeded,
 * seeding is idempotent (deterministic IDs), and nothing is duplicated across
 * app starts.
 */
class DefaultCategoriesTest {

    @Test
    fun `exactly the ten PRD categories are defined, in order`() {
        assertEquals(
            listOf(
                "Food", "Transport", "Shopping", "Bills", "Entertainment",
                "Health", "Education", "Travel", "Personal", "Other",
            ),
            DefaultCategories.definitions.map { it.name },
        )
    }

    @Test
    fun `ids are deterministic and unique - the idempotency guarantee`() {
        val ids = DefaultCategories.definitions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("default-") })
    }

    @Test
    fun `seeded rows are active, default, expense categories without caps`() {
        val now = Instant.parse("2024-01-01T00:00:00Z")
        val entities = DefaultCategories.entities(now)
        assertEquals(10, entities.size)
        entities.forEach { entity ->
            assertTrue(entity.isDefault)
            assertFalse(entity.isArchived)
            assertEquals(CategoryType.EXPENSE, entity.type)
            assertNull(entity.budgetLimitMinor)
            assertEquals(now, entity.createdAt)
            assertEquals(now, entity.updatedAt)
            assertTrue(entity.iconKey.isNotBlank())
        }
    }

    @Test
    fun `definitions are stable across calls - reseeding produces the same ids`() {
        val first = DefaultCategories.definitions.map { it.id }
        val second = DefaultCategories.definitions.map { it.id }
        assertEquals(first, second)
    }
}
