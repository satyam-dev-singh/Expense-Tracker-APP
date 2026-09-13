package com.left.app

import android.app.Application
import com.left.app.core.database.seed.DatabaseSeeder
import com.left.app.core.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Offline-first (PRD §22): the Room database is the single source of truth.
 * Default categories are seeded on every cold start via [DatabaseSeeder];
 * seeding is idempotent (deterministic IDs + INSERT OR IGNORE), so no
 * duplicates are ever created (PRD §17).
 */
@HiltAndroidApp
class LeftApplication : Application() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            databaseSeeder.seedDefaultCategories()
        }
    }
}
