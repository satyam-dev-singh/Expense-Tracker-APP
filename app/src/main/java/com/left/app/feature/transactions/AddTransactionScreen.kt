package com.left.app.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.R
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.theme.LeftTheme
import java.time.ZoneOffset

/**
 * S07 Add Transaction (PRD FR-03): amount-first quick entry built on
 * [TransactionFormFields]. Saves through the AddTransaction use case; on
 * success the saved flag navigates back.
 */
@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LeftTheme.spacing

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    if (state.showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = viewModel::onDatePickerDismiss,
            confirmButton = {
                TextButton(
                    onClick = { pickerState.selectedDateMillis?.let(viewModel::onDateSelected) },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDatePickerDismiss) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = LeftIcons.Back,
                    contentDescription = stringResource(R.string.content_description_back),
                )
            }
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(
                text = "Add transaction",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(spacing.lg))

        TransactionFormFields(
            amountInput = state.amountInput,
            amountError = state.amountError,
            onAmountChange = viewModel::onAmountChange,
            type = state.type,
            onTypeChange = viewModel::onTypeChange,
            currencyCode = state.currencyCode,
            categoryId = state.categoryId,
            categories = state.categories,
            onCategoryChange = viewModel::onCategoryChange,
            merchant = state.merchant,
            onMerchantChange = viewModel::onMerchantChange,
            note = state.note,
            onNoteChange = viewModel::onNoteChange,
            date = state.date,
            onDateClick = viewModel::onDateClick,
            isRecurring = state.isRecurring,
            onRecurringChange = viewModel::onRecurringChange,
        )

        state.saveError?.let { error ->
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(spacing.xl))
        LeftPrimaryButton(
            text = if (state.saving) "Saving…" else "Save transaction",
            onClick = viewModel::onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
    }
}
