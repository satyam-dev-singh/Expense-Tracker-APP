package com.left.app.feature.voice

import com.left.app.core.data.fake.FakeCategoryRepository
import com.left.app.core.data.fake.FakeTransactionRepository
import com.left.app.core.data.fake.FakeUserProfileRepository
import com.left.app.core.domain.AddTransaction
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.utils.Money
import com.left.app.testutil.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Voice flow tests (PRD FR-04, Phase 5): permission states, parse → confirm,
 * editable confirmation, VOICE source on save, duplicate protection with reset
 * on a new listening round, graceful error states.
 */
class VoiceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2024-03-10T10:15:00Z"), ZoneId.of("Asia/Kolkata"))

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var viewModel: VoiceViewModel

    @Before
    fun setUp() = runTest {
        transactionRepository = FakeTransactionRepository()
        categoryRepository = FakeCategoryRepository(clock)
        categoryRepository.ensureDefaultCategories()
        viewModel = VoiceViewModel(
            addTransaction = AddTransaction(transactionRepository, categoryRepository, clock),
            categoryRepository = categoryRepository,
            userProfileRepository = FakeUserProfileRepository(clock),
            clock = clock,
        )
    }

    @Test
    fun `starts asking for permission`() {
        assertEquals(VoicePhase.PERMISSION, viewModel.uiState.value.phase)
    }

    @Test
    fun `permission grant starts listening, denial shows the fallback message`() {
        viewModel.onPermissionGranted()
        assertEquals(VoicePhase.LISTENING, viewModel.uiState.value.phase)

        viewModel.onPermissionDenied()
        assertEquals(VoicePhase.ERROR, viewModel.uiState.value.phase)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `unavailable recognizer and recognition errors land in friendly error states`() {
        viewModel.onRecognizerUnavailable()
        assertEquals(VoicePhase.ERROR, viewModel.uiState.value.phase)

        viewModel.onStartListening()
        viewModel.onRecognizerError()
        assertEquals(VoicePhase.ERROR, viewModel.uiState.value.phase)
    }

    @Test
    fun `partial transcripts stream only while listening`() {
        viewModel.onPermissionGranted()
        viewModel.onPartialTranscript("spent 250 on")
        assertEquals("spent 250 on", viewModel.uiState.value.transcript)
    }

    @Test
    fun `final transcript parses into an editable confirmation`() = runTest {
        viewModel.onPermissionGranted()
        viewModel.onFinalTranscript("Spent 250 on food at chai point")

        val state = viewModel.uiState.value
        assertEquals(VoicePhase.CONFIRMING, state.phase)
        assertEquals(25_000L, Money.parse(state.amountInput, state.currencyCode).minorUnits)
        assertEquals(TransactionType.EXPENSE, state.type)
        assertEquals("default-food", state.categoryId)
        assertEquals("Chai Point", state.merchant)
        assertEquals(LocalDate.of(2024, 3, 10), state.date)
        assertEquals("Spent 250 on food at chai point", state.note)
    }

    @Test
    fun `unparseable amount asks the user to retry with an example`() {
        viewModel.onPermissionGranted()
        viewModel.onFinalTranscript("bought groceries")
        val state = viewModel.uiState.value
        assertEquals(VoicePhase.ERROR, state.phase)
        assertTrue(state.errorMessage!!.contains("amount"))
    }

    @Test
    fun `save persists with VOICE source after user edits`() = runTest {
        viewModel.onPermissionGranted()
        viewModel.onFinalTranscript("spent 250 on food at chai point")
        viewModel.onAmountChange("300") // user corrects the amount
        viewModel.onCategoryChange("default-food")
        viewModel.onSave()

        val stored = transactionRepository.observeAll().first().single()
        assertEquals(30_000L, stored.amount.minorUnits)
        assertEquals(TransactionSource.VOICE, stored.source)
        assertEquals("default-food", stored.categoryId)
        assertEquals("Chai Point", stored.merchant)
        assertEquals(VoicePhase.SAVED, viewModel.uiState.value.phase)
    }

    @Test
    fun `the same draft cannot be saved twice`() = runTest {
        viewModel.onPermissionGranted()
        viewModel.onFinalTranscript("spent 250 on food")
        viewModel.onSave()
        viewModel.onSave() // phase guard: already SAVED
        assertEquals(1, transactionRepository.observeAll().first().size)
    }

    @Test
    fun `a new listening round resets duplicate protection - re-speaking is a new entry`() = runTest {
        viewModel.onPermissionGranted()
        viewModel.onFinalTranscript("spent 250 on food")
        viewModel.onSave()
        assertEquals(VoicePhase.SAVED, viewModel.uiState.value.phase)

        // User deliberately speaks the same thing again (e.g. daily chai).
        viewModel.onStartListening()
        assertEquals(VoicePhase.LISTENING, viewModel.uiState.value.phase)
        viewModel.onFinalTranscript("spent 250 on food")
        viewModel.onSave()

        assertEquals(2, transactionRepository.observeAll().first().size)
        assertEquals(VoicePhase.SAVED, viewModel.uiState.value.phase)
    }

    @Test
    fun `invalid edited amount blocks saving with an inline error`() = runTest {
        viewModel.onPermissionGranted()
        viewModel.onFinalTranscript("spent 250 on food")
        viewModel.onAmountChange("abc")
        viewModel.onSave()

        val state = viewModel.uiState.value
        assertEquals(VoicePhase.CONFIRMING, state.phase)
        assertNotNull(state.amountError)
        assertTrue(transactionRepository.observeAll().first().isEmpty())
    }
}
