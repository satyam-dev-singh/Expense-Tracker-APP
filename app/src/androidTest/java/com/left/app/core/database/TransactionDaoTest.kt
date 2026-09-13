package com.left.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.left.app.core.database.dao.CategoryDao
import com.left.app.core.database.dao.TransactionDao
import com.left.app.core.database.entity.CategoryEntity
import com.left.app.core.database.entity.TransactionEntity
import com.left.app.core.model.CategoryType
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room tests for TransactionDao (Technical Architecture §9:
 * "Integration tests — Room CRUD"). Runs against a real in-memory SQLite
 * database on-device, exercising the actual generated SQL.
 */
@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var database: LeftDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LeftDatabase::class.java).build()
        transactionDao = database.transactionDao()
        categoryDao = database.categoryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun entity(
        id: String,
        type: TransactionType = TransactionType.EXPENSE,
        amountMinor: Long = 1_000L,
        date: LocalDate = LocalDate.of(2024, 3, 15),
        categoryId: String? = null,
        merchant: String? = null,
        note: String? = null,
    ) = TransactionEntity(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currencyCode = "INR",
        categoryId = categoryId,
        merchant = merchant,
        note = note,
        transactionDate = date,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        source = TransactionSource.MANUAL,
        isRecurring = false,
    )

    @Test
    fun insertAndGetById() = runTest {
        transactionDao.insert(entity(id = "t1", amountMinor = 35_000, merchant = "Tea stall"))
        val loaded = transactionDao.getById("t1")
        assertNotNull(loaded)
        assertEquals(35_000L, loaded!!.amountMinor)
        assertEquals("Tea stall", loaded.merchant)
        assertEquals(TransactionType.EXPENSE, loaded.type)
    }

    @Test
    fun updateChangesStoredRow() = runTest {
        transactionDao.insert(entity(id = "t1"))
        transactionDao.update(entity(id = "t1", amountMinor = 99_900, note = "corrected"))
        val loaded = transactionDao.getById("t1")
        assertEquals(99_900L, loaded!!.amountMinor)
        assertEquals("corrected", loaded.note)
    }

    @Test
    fun deleteByIdRemovesRow() = runTest {
        transactionDao.insert(entity(id = "t1"))
        transactionDao.deleteById("t1")
        assertNull(transactionDao.getById("t1"))
    }

    @Test
    fun getByMonthRespectsExactBoundaries() = runTest {
        transactionDao.insert(entity(id = "feb29", date = LocalDate.of(2024, 2, 29)))
        transactionDao.insert(entity(id = "mar1", date = LocalDate.of(2024, 3, 1)))
        transactionDao.insert(entity(id = "mar31", date = LocalDate.of(2024, 3, 31)))
        transactionDao.insert(entity(id = "apr1", date = LocalDate.of(2024, 4, 1)))

        val march = transactionDao.getByMonth(2024, 3)
        assertEquals(setOf("mar1", "mar31"), march.map { it.id }.toSet())

        val february = transactionDao.getByMonth(2024, 2)
        assertEquals(listOf("feb29"), february.map { it.id })
    }

    @Test
    fun searchMatchesMerchantAndNote() = runTest {
        transactionDao.insert(entity(id = "t1", merchant = "Domino's"))
        transactionDao.insert(entity(id = "t2", note = "team lunch"))
        transactionDao.insert(entity(id = "t3", merchant = "Metro"))

        assertEquals(listOf("t1"), transactionDao.search("domino").first().map { it.id })
        assertEquals(listOf("t2"), transactionDao.search("LUNCH").first().map { it.id })
        assertTrue(transactionDao.search("nonexistent").first().isEmpty())
    }

    @Test
    fun monthlyTotalsAggregateByType() = runTest {
        transactionDao.insert(entity(id = "i1", type = TransactionType.INCOME, amountMinor = 5_000_000, date = LocalDate.of(2024, 3, 1)))
        transactionDao.insert(entity(id = "e1", amountMinor = 1_200_000, date = LocalDate.of(2024, 3, 5)))
        transactionDao.insert(entity(id = "e2", amountMinor = 800_000, date = LocalDate.of(2024, 3, 31)))
        transactionDao.insert(entity(id = "april", amountMinor = 999_999, date = LocalDate.of(2024, 4, 1)))

        val sums = transactionDao.getMonthlyTotals(2024, 3).associate { it.type to it.totalMinor }
        assertEquals(5_000_000L, sums[TransactionType.INCOME])
        assertEquals(2_000_000L, sums[TransactionType.EXPENSE])

        val aprilSums = transactionDao.getMonthlyTotals(2024, 4).associate { it.type to it.totalMinor }
        assertEquals(999_999L, aprilSums[TransactionType.EXPENSE])
        assertNull(aprilSums[TransactionType.INCOME])
    }

    @Test
    fun deletingCategoryKeepsHistoryWithNullCategory() = runTest {
        val category = CategoryEntity(
            id = "cat-x",
            name = "Temp",
            iconKey = "category",
            type = CategoryType.EXPENSE,
            budgetLimitMinor = null,
            isDefault = false,
            isArchived = false,
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        categoryDao.upsert(category)
        transactionDao.insert(entity(id = "t1", categoryId = "cat-x"))

        // Hard delete (archiving is preferred, but history must survive even this).
        categoryDao.delete(category)

        val loaded = transactionDao.getById("t1")
        assertNotNull("historical transaction must survive category deletion", loaded)
        assertNull(loaded!!.categoryId)
    }

    @Test
    fun observeRecentLimitsAndOrdersNewestFirst() = runTest {
        transactionDao.insert(entity(id = "old", date = LocalDate.of(2024, 3, 1)))
        transactionDao.insert(entity(id = "mid", date = LocalDate.of(2024, 3, 10)))
        transactionDao.insert(entity(id = "new", date = LocalDate.of(2024, 3, 20)))

        val recent = transactionDao.observeRecent(2).first()
        assertEquals(listOf("new", "mid"), recent.map { it.id })
    }
}
