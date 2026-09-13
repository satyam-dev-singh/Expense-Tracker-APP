package com.left.app.feature.transactions

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.domain.AddTransaction
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.testutil.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Add-transaction tests (S07, PRD FR-03): defaults, inline validation, save
 * through the use case (including its own validation surfacing), double-tap
 * guard, date picker day mapping.
 */
class AddTransactionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var userProfileRepository: FakeUserProfileRepository
    private lateinit var viewModel: AddTransactionViewModel

    @Before
    fun setUp() {
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository(clock)
        userProfileRepository = FakeUserProfileRepository(clock)
        kotlinx.coroutines.runBlocking { categoryRepository.ensureDefaultCategories() }
        viewModel = AddTransactionViewModel(
            addTransaction = AddTransaction(transactionRepository, categoryRepository, clock),
            categoryRepository = categoryRepository,
            userProfileRepository = userProfileRepository,
            clock = clock,
        )
    }

    @Test
    fun `defaults are expense, today from the injected clock, INR`() {
        val state = viewModel.uiState.value
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals(LocalDate.of(2024, 3, 10), state.date)
        assertEquals("INR", state.currencyCode)
        assertNull(state.categoryId)
        assertEquals(10, state.categories.size)
    }

    @Test
    fun `currency follows the profile`() = runTest {
        userProfileRepository.upsertProfile("Satyam", "USD", "en-US")
        assertEquals("USD", viewModel.uiState.value.currencyCode)
    }

    @Test
    fun `blank amount is blocked with an error`() = runTest {
        viewModel.onSave()
        val state = viewModel.uiState.value
        assertNotNull(state.amountError)
        assertFalse(state.saved)
        assertTrue(transactionRepository.observeAll().first().isEmpty())
    }

    @Test
    fun `malformed and non-positive amounts are blocked`() {
        viewModel.onAmountChange("abc")
        viewModel.onSave()
        assertNotNull(viewModel.uiState.value.amountError)

        viewModel.onAmountChange("0")
        viewModel.onSave()
        assertEquals("Amount must be greater than zero", viewModel.uiState.value.amountError)

        viewModel.onAmountChange("42") // editing clears the error
        assertNull(viewModel.uiState.value.amountError)
    }

    @Test
    fun `happy path saves through the use case with trimmed fields`() = runTest {
        viewModel.onAmountChange("350.50")
        viewModel.onTypeChange(TransactionType.INCOME)
        viewModel.onCategoryChange("default-food")
        viewModel.onMerchantChange("  Tea stall  ")
        viewModel.onNoteChange("") // blank becomes null
        viewModel.onDateSelected(
            LocalDate.of(2024, 3, 8).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        viewModel.onRecurringChange(true)
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertTrue(state.saved)
        assertFalse(state.showDatePicker)

        val stored = transactionRepository.observeAll().first().single()
        assertEquals(35_050L, stored.amount.minorUnits)
        assertEquals(TransactionType.INCOME, stored.type)
        assertEquals("default-food", stored.categoryId)
        assertEquals("Tea stall", stored.merchant) // trimmed by the use case
        assertNull(stored.note)
        assertEquals(LocalDate.of(2024, 3, 8), stored.date)
        assertEquals(TransactionSource.MANUAL, stored.source)
        assertTrue(stored.isRecurring)
    }

    @Test
    fun `double tap on save stores exactly one transaction`() = runTest {
        viewModel.onAmountChange("10")
        viewModel.onSave()
        viewModel.onSave()
        assertTrue(viewModel.uiState.value.saved)
        assertEquals(1, transactionRepository.observeAll().first().size)
    }

    @Test
    fun `use case validation surfaces a user-safe message`() = runTest {
        viewModel.onAmountChange("10")
        viewModel.onCategoryChange("missing-category")
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertFalse(state.saved)
        assertEquals("The selected category no longer exists", state.saveError)
    }
}
