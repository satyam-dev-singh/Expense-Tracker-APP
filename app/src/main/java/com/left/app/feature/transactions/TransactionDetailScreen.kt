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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.R
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.component.LeftTextButton
import com.left.app.core.designsystem.moneyHero
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.model.TransactionSource
import com.left.app.core.model.TransactionType
import java.time.ZoneOffset

/**
 * S09 Transaction Detail (PRD FR-03): view the full record, edit in place,
 * delete with confirmation. View and edit share [TransactionFormFields]; no
 * business logic here.
 */
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    // transactionId arrives via the navigation SavedStateHandle (see LeftNavHost);
    // the parameter documents the route contract.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LeftTheme.spacing

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    if (state.showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (state.date ?: state.transaction?.date)
                ?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
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

    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismiss,
            title = { Text("Delete this transaction?") },
            text = { Text("This can’t be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirm) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismiss) { Text("Cancel") }
            },
        )
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
                text = if (state.editing) "Edit transaction" else "Transaction",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (!state.loading && !state.notFound && !state.deleted) {
                LeftTextButton(
                    text = if (state.editing) "Cancel" else "Edit",
                    onClick = if (state.editing) viewModel::onCancelEdit else viewModel::onEditClick,
                    enabled = !state.saving,
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.lg))

        when {
            state.notFound -> {
                Text(
                    text = "This transaction no longer exists.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.loading -> {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.editing -> {
                TransactionFormFields(
                    amountInput = state.amountInput,
                    amountError = state.amountError,
                    onAmountChange = viewModel::onAmountChange,
                    type = state.type,
                    onTypeChange = viewModel::onTypeChange,
                    currencyCode = state.currencyCode,
                    categoryId = state.categoryId,
                    // Active categories from state; the row's current selection
                    // stays valid even if its category was archived.
                    categories = state.categories,
                    onCategoryChange = viewModel::onCategoryChange,
                    merchant = state.merchant,
                    onMerchantChange = viewModel::onMerchantChange,
                    note = state.note,
                    onNoteChange = viewModel::onNoteChange,
                    date = state.date ?: state.transaction?.date ?: java.time.LocalDate.now(),
                    onDateClick = viewModel::onDateClick,
                    isRecurring = state.isRecurring,
                    onRecurringChange = viewModel::onRecurringChange,
                )
                Spacer(modifier = Modifier.height(spacing.xl))
                LeftPrimaryButton(
                    text = if (state.saving) "Saving…" else "Save changes",
                    onClick = viewModel::onSaveEdit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                )
            }
            else -> {
                val transaction = state.transaction ?: return@Column
                val isIncome = transaction.type == TransactionType.INCOME
                Text(
                    text = (if (isIncome) "+" else "−") + transaction.amount.format(state.currencyCode),
                    style = MaterialTheme.typography.moneyHero,
                    color = if (isIncome) {
                        LeftTheme.extendedColors.income
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                Spacer(modifier = Modifier.height(spacing.lg))
                DetailRow("Category", state.category?.name ?: "Uncategorized")
                transaction.merchant?.let { DetailRow("Merchant", it) }
                transaction.note?.let { DetailRow("Note", it) }
                DetailRow("Date", transaction.date.format(DATE_FORMATTER))
                DetailRow("Type", if (isIncome) "Income" else "Expense")
                if (transaction.isRecurring) DetailRow("Recurring", "Yes")
                DetailRow(
                    "Source",
                    when (transaction.source) {
                        TransactionSource.MANUAL -> "Manual"
                        TransactionSource.VOICE -> "Voice"
                        TransactionSource.IMPORT -> "Import"
                    },
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
                LeftTextButton(
                    text = "Delete transaction",
                    onClick = viewModel::onDeleteClick,
                    enabled = !state.saving,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val spacing = LeftTheme.spacing
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
