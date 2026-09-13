package com.left.app.core.data

import com.left.app.core.database.dao.CategoryDao
import com.left.app.core.database.entity.CategoryEntity
import com.left.app.core.database.seed.DefaultCategories
import com.left.app.core.model.Category
import com.left.app.core.model.CategoryType
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Domain-level access to categories. */
interface CategoryRepository {
    fun observeActiveCategories(): Flow<List<Category>>
    fun observeAllCategories(): Flow<List<Category>>
    suspend fun getById(id: String): Category?
    suspend fun exists(id: String): Boolean
    suspend fun createCategory(name: String, iconKey: String, type: CategoryType, budgetLimit: Money?): String
    suspend fun updateCategory(category: Category)
    suspend fun archive(id: String)
    suspend fun unarchive(id: String)

    /**
     * Idempotent default-category seeding (PRD §17): deterministic IDs plus
     * INSERT OR IGNORE — calling this on every app start never duplicates rows.
     */
    suspend fun ensureDefaultCategories()
}

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val clock: Clock,
) : CategoryRepository {

    override fun observeActiveCategories(): Flow<List<Category>> =
        categoryDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeAllCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun exists(id: String): Boolean = categoryDao.exists(id)

    override suspend fun createCategory(
        name: String,
        iconKey: String,
        type: CategoryType,
        budgetLimit: Money?,
    ): String {
        val now = Instant.now(clock)
        val entity = CategoryEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            iconKey = iconKey,
            type = type,
            budgetLimitMinor = budgetLimit?.minorUnits,
            isDefault = false,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        categoryDao.upsert(entity)
        return entity.id
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.upsert(category.toEntity().copy(updatedAt = Instant.now(clock)))
    }

    override suspend fun archive(id: String) = categoryDao.archive(id, Instant.now(clock))

    override suspend fun unarchive(id: String) = categoryDao.unarchive(id, Instant.now(clock))

    override suspend fun ensureDefaultCategories() {
        categoryDao.insertAllIgnore(DefaultCategories.entities(Instant.now(clock)))
    }

    private fun CategoryEntity.toDomain(): Category = Category(
        id = id,
        name = name,
        iconKey = iconKey,
        type = type,
        budgetLimit = budgetLimitMinor?.let(Money::ofMinorUnits),
        isDefault = isDefault,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Category.toEntity(): CategoryEntity = CategoryEntity(
        id = id,
        name = name,
        iconKey = iconKey,
        type = type,
        budgetLimitMinor = budgetLimit?.minorUnits,
        isDefault = isDefault,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
