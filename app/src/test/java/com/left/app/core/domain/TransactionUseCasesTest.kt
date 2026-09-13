package com.left.app.core.domain

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.model.CategoryType
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Transaction use-case tests (PRD §19): expense insertion, income insertion,
 * update, delete, and the validation rules of PRD §18.
 */
class TransactionUseCasesTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var addTransaction: AddTransaction
    private lateinit var updateTransaction: UpdateTransaction
    private lateinit var deleteTransaction: DeleteTransaction
    private lateinit var getMonthlyTransactions: GetMonthlyTransactions

    @Before
    fun setUp() {
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository(clock)
        addTransaction = AddTransaction(transactionRepository, categoryRepository, clock)
        updateTransaction = UpdateTransaction(transactionRepository, categoryRepository, clock)
        deleteTransaction = DeleteTransaction(transactionRepository)
        getMonthlyTransactions = GetMonthlyTransactions(transactionRepository)
    }

    private fun newTransaction(
        type: TransactionType = TransactionType.EXPENSE,
        amount: Money = Money.ofMinorUnits(35_000), // ₹350.00
        currencyCode: String = "INR",
        categoryId: String? = null,
        date: LocalDate = LocalDate.of(2024, 3, 10),
    ) = NewTransaction(
        type = type,
        amount = amount,
        currencyCode = currencyCode,
        categoryId = categoryId,
        merchant = "Tea stall",
        note = null,
        date = date,
    )

    // ---- Insert ----

    @Test
    fun `expense insertion stores all fields with fixed-clock timestamps`() = runTest {
        val id = addTransaction(newTransaction())

        val stored = transactionRepository.getById(id)
        assertNotNull(stored)
        stored!!
        assertEquals(TransactionType.EXPENSE, stored.type)
        assertEquals(35_000L, stored.amount.minorUnits)
        assertEquals("INR", stored.currencyCode)
        assertEquals(TransactionSource.MANUAL, stored.source)
        assertEquals(Instant.now(clock), stored.createdAt)
        assertEquals(Instant.now(clock), stored.updatedAt)
    }

    @Test
    fun `income insertion works like expense insertion`() = runTest {
        val id = addTransaction(newTransaction(type = TransactionType.INCOME, amount = Money.ofMinorUnits(5_000_000)))

        val stored = transactionRepository.getById(id)
        assertEquals(TransactionType.INCOME, stored?.type)
        assertEquals(5_000_000L, stored?.amount?.minorUnits)
    }

    @Test
    fun `insertion with an existing category succeeds`() = runTest {
        val categoryId = categoryRepository.createCategory("Food", "restaurant", CategoryType.EXPENSE, null)
        val id = addTransaction(newTransaction(categoryId = categoryId))
        assertEquals(categoryId, transactionRepository.getById(id)?.categoryId)
    }

    // ---- Validation (PRD §18) ----

    @Test
    fun `zero amount is rejected`() {
        assertThrows(TransactionValidationException.NonPositiveAmount::class.java) {
            runTest { addTransaction(newTransaction(amount = Money.ZERO)) }
        }
    }

    @Test
    fun `negative amount is rejected`() {
        assertThrows(TransactionValidationException.NonPositiveAmount::class.java) {
            runTest { addTransaction(newTransaction(amount = Money.ofMinorUnits(-100))) }
        }
    }

    @Test
    fun `invalid currency is rejected`() {
        assertThrows(TransactionValidationException.InvalidCurrency::class.java) {
            runTest { addTransaction(newTransaction(currencyCode = "XYZ")) }
        }
        assertThrows(TransactionValidationException.InvalidCurrency::class.java) {
            runTest { addTransaction(newTransaction(currencyCode = "inr")) }
        }
    }

    @Test
    fun `unknown category is rejected`() {
        assertThrows(TransactionValidationException.UnknownCategory::class.java) {
            runTest { addTransaction(newTransaction(categoryId = "missing-category")) }
        }
    }

    @Test
    fun `null category is explicitly allowed (uncategorized)`() = runTest {
        val id = addTransaction(newTransaction(categoryId = null))
        assertNull(transactionRepository.getById(id)?.categoryId)
    }

    // ---- Update ----

    @Test
    fun `update changes the stored transaction`() = runTest {
        val id = addTransaction(newTransaction())
        val updated = transactionRepository.getById(id)!!.copy(
            amount = Money.ofMinorUnits(40_000),
            note = "updated",
        )
        updateTransaction(updated)

        val stored = transactionRepository.getById(id)!!
        assertEquals(40_000L, stored.amount.minorUnits)
        assertEquals("updated", stored.note)
    }

    @Test
    fun `updating a missing transaction fails safely`() {
        assertThrows(TransactionValidationException.NotFound::class.java) {
            runTest {
                val id = addTransaction(newTransaction())
                val ghost = transactionRepository.getById(id)!!.copy(id = "does-not-exist")
                updateTransaction(ghost)
            }
        }
    }

    @Test
    fun `update validates the new values`() {
        assertThrows(TransactionValidationException.NonPositiveAmount::class.java) {
            runTest {
                val id = addTransaction(newTransaction())
                updateTransaction(transactionRepository.getById(id)!!.copy(amount = Money.ZERO))
            }
        }
    }

    // ---- Delete ----

    @Test
    fun `delete removes the transaction`() = runTest {
        val id = addTransaction(newTransaction())
        deleteTransaction(id)
        assertNull(transactionRepository.getById(id))
    }

    @Test
    fun `deleting a missing transaction fails safely`() {
        assertThrows(TransactionValidationException.NotFound::class.java) {
            runTest { deleteTransaction("does-not-exist") }
        }
    }

    // ---- Monthly reads ----

    @Test
    fun `getMonthlyTransactions returns only that month, boundaries included`() = runTest {
        addTransaction(newTransaction(date = LocalDate.of(2024, 2, 29))) // leap day — previous month
        val marchFirst = addTransaction(newTransaction(date = LocalDate.of(2024, 3, 1)))
        val marchLast = addTransaction(newTransaction(date = LocalDate.of(2024, 3, 31)))
        addTransaction(newTransaction(date = LocalDate.of(2024, 4, 1))) // next month

        val march = getMonthlyTransactions(2024, 3).first()
        assertEquals(setOf(marchFirst, marchLast), march.map { it.id }.toSet())
    }

    @Test
    fun `getMonthlyTransactions rejects invalid months`() {
        assertThrows(IllegalArgumentException::class.java) {
            getMonthlyTransactions(2024, 13)
        }
    }

    @Test
    fun `blank merchant and note are stored as null, not empty strings`() = runTest {
        val id = addTransaction(newTransaction().copy(merchant = "   ", note = ""))
        val stored = transactionRepository.getById(id)!!
        assertNull(stored.merchant)
        assertNull(stored.note)
    }

    @Test
    fun `getTransactions streams everything`() = runTest {
        addTransaction(newTransaction())
        addTransaction(newTransaction(type = TransactionType.INCOME))
        val all = GetTransactions(transactionRepository)().first()
        assertEquals(2, all.size)
        assertTrue(all.any { it.type == TransactionType.INCOME })
    }
}
