package com.left.app.core.database.seed

import com.left.app.core.data.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds first-run data. Currently only the default categories (PRD §17).
 *
 * Invoked from [com.left.app.LeftApplication.onCreate] on the application
 * coroutine scope. Seeding is idempotent — [CategoryRepository.ensureDefaultCategories]
 * uses deterministic IDs plus INSERT OR IGNORE, so repeated calls are safe and
 * never duplicate rows.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    suspend fun seedDefaultCategories() {
        categoryRepository.ensureDefaultCategories()
    }
}
