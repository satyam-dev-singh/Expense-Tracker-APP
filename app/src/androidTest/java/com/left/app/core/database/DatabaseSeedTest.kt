package com.left.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.left.app.core.database.dao.CategoryDao
import com.left.app.core.database.seed.DefaultCategories
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the default-category seeding guarantee (PRD §17): categories are
 * seeded exactly once — re-seeding on every app start creates no duplicates.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSeedTest {

    private lateinit var database: LeftDatabase
    private lateinit var categoryDao: CategoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LeftDatabase::class.java).build()
        categoryDao = database.categoryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun seedingTwiceProducesExactlyTenCategories() = runTest {
        val firstRun = Instant.parse("2024-01-01T00:00:00Z")
        val laterRun = Instant.parse("2024-06-15T08:30:00Z")

        categoryDao.insertAllIgnore(DefaultCategories.entities(firstRun))
        // Simulates every subsequent app start: same IDs, newer timestamps.
        categoryDao.insertAllIgnore(DefaultCategories.entities(laterRun))
        categoryDao.insertAllIgnore(DefaultCategories.entities(laterRun))

        val all = categoryDao.observeAll().first()
        assertEquals(DefaultCategories.definitions.size, all.size)
        // Original insert wins (IGNORE) — creation timestamps are preserved.
        assertEquals(firstRun, all.first { it.id == "default-food" }.createdAt)
    }
}
