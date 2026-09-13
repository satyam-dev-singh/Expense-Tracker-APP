package com.left.app.feature.transactions

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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the add-transaction form. */
data class AddTransactionUiState(
    val amountInput: String = "",
    val amountError: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: String? = null,
    val merchant: String = "",
    val note: String = "",
    val date: LocalDate,
    val showDatePicker: Boolean = false,
    val isRecurring: Boolean = false,
    val categories: List<Category> = emptyList(),
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,
)

/**
 * S07 Add Transaction (PRD FR-03). Validates via [Money.parse], persists via
 * the [AddTransaction] use case (which re-validates per PRD §18). [saved] is a
 * one-way completion signal the screen turns into navigation.
 */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val addTransaction: AddTransaction,
    categoryRepository: CategoryRepository,
    userProfileRepository: UserProfileRepository,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddTransactionUiState(date = LocalDate.now(clock)))
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

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

    fun onAmountChange(value: String) {
        _uiState.update { it.copy(amountInput = value, amountError = null) }
    }

    fun onTypeChange(value: TransactionType) {
        _uiState.update { it.copy(type = value) }
    }

    fun onCategoryChange(categoryId: String?) {
        _uiState.update { it.copy(categoryId = categoryId) }
    }

    fun onMerchantChange(value: String) {
        _uiState.update { it.copy(merchant = value) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    fun onRecurringChange(value: Boolean) {
        _uiState.update { it.copy(isRecurring = value) }
    }

    fun onDateClick() {
        _uiState.update { it.copy(showDatePicker = true) }
    }

    fun onDatePickerDismiss() {
        _uiState.update { it.copy(showDatePicker = false) }
    }

    /** DatePicker returns UTC millis at midnight; converting via UTC yields the picked calendar day. */
    fun onDateSelected(epochMillis: Long) {
        val picked = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        _uiState.update { it.copy(date = picked, showDatePicker = false) }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.saving || state.saved) return

        val amount = when {
            state.amountInput.isBlank() -> {
                _uiState.update { it.copy(amountError = "Enter an amount") }
                return
            }
            else -> try {
                Money.parse(state.amountInput, state.currencyCode)
            } catch (e: MoneyParseException) {
                _uiState.update { it.copy(amountError = "Enter a valid amount") }
                return
            }
        }
        if (!amount.isPositive) {
            _uiState.update { it.copy(amountError = "Amount must be greater than zero") }
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
                        date = state.date,
                        source = TransactionSource.MANUAL,
                        isRecurring = state.isRecurring,
                    ),
                )
                _uiState.update { it.copy(saving = false, saved = true) }
            } catch (e: TransactionValidationException) {
                // Use-case messages are user-safe by contract (PRD §18/§20).
                _uiState.update { it.copy(saving = false, saveError = e.message) }
            } catch (e: Exception) {
                SafeLogger.w(TAG, "add transaction failed", e)
                _uiState.update {
                    it.copy(saving = false, saveError = "Couldn’t save the transaction. Please try again.")
                }
            }
        }
    }

    private companion object {
        const val TAG = "AddTransaction"
    }
}
