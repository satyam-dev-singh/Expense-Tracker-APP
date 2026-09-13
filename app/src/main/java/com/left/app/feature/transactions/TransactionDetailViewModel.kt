package com.left.app.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.UserProfileRepository
import com.left.app.core.domain.DeleteTransaction
import com.left.app.core.domain.GetTransaction
import com.left.app.core.domain.TransactionValidationException
import com.left.app.core.domain.UpdateTransaction
import com.left.app.core.model.Category
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionType
import com.left.app.core.security.SafeLogger
import com.left.app.core.utils.CurrencyUtils
import com.left.app.core.utils.Money
import com.left.app.core.utils.MoneyParseException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the transaction detail / edit screen. */
data class TransactionDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val transaction: Transaction? = null,
    val category: Category? = null,
    val categories: List<Category> = emptyList(),
    val currencyCode: String = CurrencyUtils.DEFAULT_CURRENCY_CODE,
    val editing: Boolean = false,
    val amountInput: String = "",
    val amountError: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: String? = null,
    val merchant: String = "",
    val note: String = "",
    val date: LocalDate? = null,
    val showDatePicker: Boolean = false,
    val isRecurring: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    val showDeleteConfirm: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * S09 Transaction Detail (PRD FR-03): view, edit, delete. Streams the row via
 * [GetTransaction]; edits go through [UpdateTransaction] (re-validated, PRD
 * §18); delete requires confirmation. Edit fields are prefilled from the row
 * whenever the user is not actively editing (so external updates never clobber
 * in-progress edits).
 */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    getTransaction: GetTransaction,
    private val updateTransaction: UpdateTransaction,
    private val deleteTransaction: DeleteTransaction,
    categoryRepository: CategoryRepository,
    userProfileRepository: UserProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val transactionId: String = checkNotNull(savedStateHandle["transactionId"])

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                getTransaction(transactionId),
                categoryRepository.observeActiveCategories(),
                userProfileRepository.observeProfile(),
            ) { transaction, categories, profile ->
                Triple(transaction, categories, profile?.currencyCode ?: CurrencyUtils.DEFAULT_CURRENCY_CODE)
            }.collect { (transaction, categories, currency) ->
                _uiState.update { state ->
                    if (transaction == null) {
                        state.copy(loading = false, notFound = !state.deleted)
                    } else {
                        val base = state.copy(
                            loading = false,
                            notFound = false,
                            transaction = transaction,
                            categories = categories,
                            category = transaction.categoryId?.let { id ->
                                categories.firstOrNull { it.id == id }
                            },
                            currencyCode = currency,
                        )
                        if (state.editing) {
                            base // keep the user's in-progress edits
                        } else {
                            base.copy(
                                amountInput = transaction.amount.format(currency),
                                type = transaction.type,
                                categoryId = transaction.categoryId,
                                merchant = transaction.merchant.orEmpty(),
                                note = transaction.note.orEmpty(),
                                date = transaction.date,
                                isRecurring = transaction.isRecurring,
                                amountError = null,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Edit mode ----

    fun onEditClick() {
        _uiState.update { it.copy(editing = true, saveError = null) }
    }

    fun onCancelEdit() {
        // Re-prefill happens automatically because the stream re-applies when not editing.
        val transaction = _uiState.value.transaction
        _uiState.update {
            it.copy(
                editing = false,
                amountError = null,
                saveError = null,
                amountInput = transaction?.amount?.format(it.currencyCode) ?: it.amountInput,
                type = transaction?.type ?: it.type,
                categoryId = transaction?.categoryId,
                merchant = transaction?.merchant.orEmpty(),
                note = transaction?.note.orEmpty(),
                date = transaction?.date,
                isRecurring = transaction?.isRecurring ?: false,
            )
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

    fun onDateSelected(epochMillis: Long) {
        val picked = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        _uiState.update { it.copy(date = picked, showDatePicker = false) }
    }

    fun onSaveEdit() {
        val state = _uiState.value
        val current = state.transaction ?: return
        if (state.saving) return

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

        _uiState.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            try {
                updateTransaction(
                    current.copy(
                        type = state.type,
                        amount = amount,
                        categoryId = state.categoryId,
                        merchant = state.merchant,
                        note = state.note,
                        date = state.date ?: current.date,
                        isRecurring = state.isRecurring,
                    ),
                )
                _uiState.update { it.copy(saving = false, editing = false) }
            } catch (e: TransactionValidationException) {
                _uiState.update { it.copy(saving = false, saveError = e.message) }
            } catch (e: Exception) {
                SafeLogger.w(TAG, "update transaction failed", e)
                _uiState.update {
                    it.copy(saving = false, saveError = "Couldn’t save the changes. Please try again.")
                }
            }
        }
    }

    // ---- Delete ----

    fun onDeleteClick() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun onDeleteDismiss() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun onDeleteConfirm() {
        if (_uiState.value.deleted) return
        _uiState.update { it.copy(showDeleteConfirm = false, saving = true) }
        viewModelScope.launch {
            try {
                deleteTransaction(transactionId)
                _uiState.update { it.copy(saving = false, deleted = true) }
            } catch (e: TransactionValidationException) {
                _uiState.update { it.copy(saving = false, saveError = e.message) }
            } catch (e: Exception) {
                SafeLogger.w(TAG, "delete transaction failed", e)
                _uiState.update {
                    it.copy(saving = false, saveError = "Couldn’t delete the transaction. Please try again.")
                }
            }
        }
    }

    private companion object {
        const val TAG = "TransactionDetail"
    }
}
