package com.left.app.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.domain.AddTransaction
import com.left.app.core.domain.NewTransaction
import com.left.app.core.domain.TransactionValidationException
import com.left.app.core.model.Category
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import com.left.app.core.utils.MoneyParseException
import com.left.app.core.voice.VoiceExpenseParser
import com.left.app.core.voice.VoiceParseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Voice capture flow phases (PRD FR-04, Phase 5). */
enum class VoicePhase { PERMISSION, LISTENING, CONFIRMING, SAVED, ERROR }

/** Immutable state for the voice quick-entry screen. */
data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.PERMISSION,
    val transcript: String = "",
    val errorMessage: String? = null,
    // Editable confirmation fields (the parts speech gets wrong most often).
    val amountInput: String = "",
    val amountError: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: String? = null,
    // Read-only parsed context.
    val merchant: String? = null,
    val note: String? = null,
    val date: LocalDate? = null,
    val categories: List<Category> = emptyList(),
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val saving: Boolean = false,
    val saveError: String? = null,
)

/**
 * Voice quick entry (PRD FR-04): permission → listen → parse → CONFIRM → save.
 * Nothing is ever written without the user confirming. Duplicate protection:
 * a parsed draft can be saved exactly once; starting a new listening round
 * resets that guard (a re-spoken utterance is a new entry, not a duplicate).
 */
@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val addTransaction: AddTransaction,
    categoryRepository: CategoryRepository,
    userProfileRepository: UserProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    /** Signature of the last saved draft — duplicate protection (FR-04). */
    private var lastSavedSignature: String? = null

    init {
        viewModelScope.launch {
            categoryRepository.observeActiveCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            userProfileRepository.observeProfile().collect { profile ->
                _uiState.update {
                    it.copy(currencyCode = profile?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE)
                }
            }
        }
    }

    // ---- Permission / capture events (driven by the composable) ----

    fun onPermissionGranted() {
        _uiState.update { it.copy(phase = VoicePhase.LISTENING, transcript = "", errorMessage = null) }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                phase = VoicePhase.ERROR,
                errorMessage = "Voice entry needs the microphone. Allow it when prompted, or type the transaction instead.",
            )
        }
    }

    fun onRecognizerUnavailable() {
        _uiState.update {
            it.copy(
                phase = VoicePhase.ERROR,
                errorMessage = "Speech recognition isn’t available on this device right now. Type the transaction instead.",
            )
        }
    }

    fun onRecognizerError() {
        _uiState.update {
            it.copy(phase = VoicePhase.ERROR, errorMessage = "I didn’t catch that. Try again, or type it instead.")
        }
    }

    /** A new listening round: the duplicate guard resets (a re-spoken entry is a new entry). */
    fun onStartListening() {
        lastSavedSignature = null
        _uiState.update {
            it.copy(
                phase = VoicePhase.LISTENING,
                transcript = "",
                errorMessage = null,
                amountError = null,
                saveError = null,
            )
        }
    }

    fun onPartialTranscript(text: String) {
        if (_uiState.value.phase == VoicePhase.LISTENING) {
            _uiState.update { it.copy(transcript = text) }
        }
    }

    /** Final recognized text → parse → confirmation. Nothing is saved here. */
    fun onFinalTranscript(text: String) {
        val state = _uiState.value
        when (val result = VoiceExpenseParser.parse(text, state.currencyCode, state.categories, LocalDate.now(clock))) {
            is VoiceParseResult.NoAmount -> _uiState.update {
                it.copy(
                    phase = VoicePhase.ERROR,
                    transcript = text,
                    errorMessage = "I didn’t catch the amount. Try saying it like “Spent 250 on food”.",
                )
            }
            is VoiceParseResult.Parsed -> {
                val draft = result.draft
                _uiState.update {
                    it.copy(
                        phase = VoicePhase.CONFIRMING,
                        transcript = text,
                        amountInput = draft.amount.format(it.currencyCode),
                        type = draft.type,
                        categoryId = draft.categoryId,
                        merchant = draft.merchant,
                        note = draft.note,
                        date = draft.date,
                        amountError = null,
                        saveError = null,
                    )
                }
            }
        }
    }

    // ---- Confirmation edits ----

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amountInput = value, amountError = null) }
    }

    fun onTypeChange(value: TransactionType) {
        _uiState.update { it.copy(type = value) }
    }

    fun onCategoryChange(categoryId: String?) {
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    // ---- Save ----

    fun onSave() {
        val state = _uiState.value
        if (state.saving || state.phase != VoicePhase.CONFIRMING) return

        val amount = try {
            Money.parse(state.amountInput, state.currencyCode)
        } catch (e: MoneyParseException) {
            _uiState.update { it.copy(amountError = "Enter a valid amount") }
            return
        }
        if (!amount.isPositive) {
            _uiState.update { it.copy(amountError = "Amount must be greater than zero") }
            return
        }

        // Duplicate protection (PRD FR-04): the same draft cannot be saved twice.
        val signature = listOf(
            amount.minorUnits, state.type, state.categoryId, state.merchant, state.note, state.date,
        ).joinToString("|")
        if (signature == lastSavedSignature) {
            _uiState.update { it.copy(saveError = "This entry was already saved.") }
            return
        }

        _uiState.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                addTransaction(
                    NewTransaction(
                        type = state.type,
                        amount = amount,
                        currencyCode = state.currencyCode,
                        categoryId = state.categoryId,
                        merchant = state.merchant,
                        note = state.note,
                        date = state.date ?: LocalDate.now(clock),
                        source = TransactionSource.VOICE,
                        isRecurring = false,
                    ),
                )
                lastSavedSignature = signature
                _uiState.update { it.copy(saving = false, phase = VoicePhase.SAVED) }
            } catch (e: TransactionValidationException) {
                _uiState.update { it.copy(saving = false, saveError = e.message) }
            } catch (e: Exception) {
                SafeLogger.w(TAG, "voice save failed", e)
                _uiState.update {
                    it.copy(saving = false, saveError = "Couldn’t save the transaction. Please try again.")
                }
            }
        }
    }

    private companion object {
        const val TAG = "Voice"
    }
}
