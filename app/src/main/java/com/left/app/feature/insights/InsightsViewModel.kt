package com.left.app.feature.insights

import androidx.lifecycle.ViewModel
import com.left.app.core.domain.extractSmartTransactionText
import com.left.app.core.domain.spendingInsight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class InsightsUiState(val input: String = "", val amountText: String? = null, val merchant: String? = null, val categoryName: String = "Other", val insight: String = "Add transactions to unlock local insights.")

/** Phase 10 local-only insights shell; intentionally no network AI/ML. */
class InsightsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()
    fun onInputChange(value: String) {
        val extraction = extractSmartTransactionText(value)
        _uiState.update { InsightsUiState(value, extraction.amountText, extraction.merchant, extraction.categoryName, spendingInsight(extraction.categoryName, 1L)) }
    }
}
