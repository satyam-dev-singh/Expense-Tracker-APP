package com.left.app.feature.transactions

import androidx.lifecycle.SavedStateHandle
import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.domain.DeleteTransaction
import com.left.app.core.domain.GetTransaction
import com.left.app.core.domain.UpdateTransaction
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import com.left.app.testutil.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Transaction detail tests (S09, PRD FR-03): load, edit-prefill (which must
 * round-trip through Money.parse), save, delete with confirmation, not-found.
 */
class TransactionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository

    @Before
    fun setUp() = runTest {
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository(clock)
        categoryRepository.ensureDefaultCategories()
        transactionRepository.insert(
            Transaction(
                id = "t1",
                type = TransactionType.EXPENSE,
                amount = Money.ofMinorUnits(35_000),
                currencyCode = "INR",
                categoryId = "default-food",
                merchant = "Tea stall",
                note = null,
                date = LocalDate.of(2024, 3, 10),
                createdAt = Instant.parse("2024-03-10T08:00:00Z"),
                updatedAt = Instant.parse("2024-03-10T08:00:00Z"),
                source = TransactionSource.MANUAL,
                isRecurring = false,
            ),
        )
    }

    private fun viewModel(id: String = "t1") = TransactionDetailViewModel(
        getTransaction = GetTransaction(transactionRepository),
        updateTransaction = UpdateTransaction(transactionRepository, categoryRepository, clock),
        deleteTransaction = DeleteTransaction(transactionRepository),
        categoryRepository = categoryRepository,
        userProfileRepository = FakeUserProfileRepository(clock),
        savedStateHandle = SavedStateHandle(mapOf("transactionId" to id)),
    )

    private fun TestScope.collectStates(vm: TransactionDetailViewModel): MutableList<TransactionDetailUiState> {
        val states = mutableListOf<TransactionDetailUiState>()
        backgroundScope.launch { vm.uiState.collect { states.add(it) } }
        return states
    }

    @Test
    fun `loads the row with category and prefills edit fields`() = runTest {
        val vm = viewModel()
        val states = collectStates(vm)
        advanceUntilIdle()
        val state = states.last()

        assertFalse(state.loading)
        assertFalse(state.notFound)
        assertEquals("t1", state.transaction?.id)
        assertEquals("Food", state.category?.name)
        // Prefill must round-trip through Money.parse (display format -> minor units).
        assertEquals(35_000L, Money.parse(state.amountInput, state.currencyCode).minorUnits)
        assertEquals(LocalDate.of(2024, 3, 10), state.date)
        assertFalse(state.editing)
    }

    @Test
    fun `unknown id shows the not-found state`() = runTest {
        val vm = viewModel(id = "ghost")
        val states = collectStates(vm)
        advanceUntilIdle()
        assertTrue(states.last().notFound)
        assertNull(states.last().transaction)
    }

    @Test
    fun `edit save updates the stored row`() = runTest {
        val vm = viewModel()
        val states = collectStates(vm)
        advanceUntilIdle()

        vm.onEditClick()
        vm.onAmountChange("500")
        vm.onMerchantChange("Cafe")
        vm.onSaveEdit()
        advanceUntilIdle()

        val stored = transactionRepository.getById("t1")!!
        assertEquals(50_000L, stored.amount.minorUnits)
        assertEquals("Cafe", stored.merchant)
        assertEquals(Instant.now(clock), stored.updatedAt)
        assertFalse(states.last().editing)
    }

    @Test
    fun `invalid edit keeps editing with an inline error`() = runTest {
        val vm = viewModel()
        val states = collectStates(vm)
        advanceUntilIdle()

        vm.onEditClick()
        vm.onAmountChange("abc")
        vm.onSaveEdit()
        advanceUntilIdle()

        val state = states.last()
        assertTrue(state.editing)
        assertNotNull(state.amountError)
        assertEquals(35_000L, transactionRepository.getById("t1")!!.amount.minorUnits)
    }

    @Test
    fun `delete requires confirmation and removes the row`() = runTest {
        val vm = viewModel()
        val states = collectStates(vm)
        advanceUntilIdle()

        vm.onDeleteClick()
        assertTrue(states.last().showDeleteConfirm)
        vm.onDeleteConfirm()
        advanceUntilIdle()

        assertTrue(states.last().deleted)
        assertNull(transactionRepository.getById("t1"))
    }

    @Test
    fun `dismissing the confirmation keeps the row`() = runTest {
        val vm = viewModel()
        val states = collectStates(vm)
        advanceUntilIdle()

        vm.onDeleteClick()
        vm.onDeleteDismiss()
        advanceUntilIdle()

        assertFalse(states.last().showDeleteConfirm)
        assertFalse(states.last().deleted)
        assertNotNull(transactionRepository.getById("t1"))
    }
}
