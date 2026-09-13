package com.left.app.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.left.app.core.database.entity.CategoryEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * DAO for categories. Default-category seeding uses [insertAllIgnore] with
 * deterministic IDs, so re-seeding never duplicates rows (PRD §17).
 */
@Dao
interface CategoryDao {

    /** Idempotent seed insert: rows whose primary key already exists are skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(categories: List<CategoryEntity>): List<Long>

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT * FROM categories WHERE is_archived = 0 ORDER BY name ASC")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY is_archived ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun archive(id: String, updatedAt: Instant)

    @Query("UPDATE categories SET is_archived = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun unarchive(id: String, updatedAt: Instant)

    /**
     * Hard delete. Prefer [archive] — the FK on transactions.category_id is
     * SET_NULL, so historical transactions survive as uncategorized (PRD §11).
     */
    @Delete
    suspend fun delete(category: CategoryEntity)
}
