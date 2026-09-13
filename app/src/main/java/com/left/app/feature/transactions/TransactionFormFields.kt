package com.left.app.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftAmountField
import com.left.app.core.designsystem.component.LeftTextField
import com.left.app.core.designsystem.component.LeftTonalButton
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.model.Category
import com.left.app.core.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Display formatting for form dates (presentation only). */
internal val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * The shared add/edit transaction form (UX/UI Spec S07, PRD FR-03):
 * amount-first, expense/income toggle, category, merchant/note, date, recurring.
 * Stateless — used by AddTransactionScreen and the detail screen's edit mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransactionFormFields(
    amountInput: String,
    amountError: String?,
    onAmountChange: (String) -> Unit,
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    currencyCode: String,
    categoryId: String?,
    categories: List<Category>,
    onCategoryChange: (String?) -> Unit,
    merchant: String,
    onMerchantChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    date: LocalDate,
    onDateClick: () -> Unit,
    isRecurring: Boolean,
    onRecurringChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(modifier = modifier) {
        // Amount first — the fastest path (UX/UI Spec §7).
        LeftAmountField(
            value = amountInput,
            onValueChange = onAmountChange,
            label = "Amount ($currencyCode)",
            isError = amountError != null,
            supportingText = amountError,
        )
        Spacer(modifier = Modifier.height(spacing.md))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = type == TransactionType.EXPENSE,
                onClick = { onTypeChange(TransactionType.EXPENSE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Expense") }
            SegmentedButton(
                selected = type == TransactionType.INCOME,
                onClick = { onTypeChange(TransactionType.INCOME) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Income") }
        }
        Spacer(modifier = Modifier.height(spacing.md))

        Text(
            text = "Category (optional)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = categoryId == category.id,
                    // Tapping the selected chip again clears it (uncategorized is valid).
                    onClick = { onCategoryChange(if (categoryId == category.id) null else category.id) },
                    label = { Text(category.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = LeftIcons.category(category.iconKey),
                            contentDescription = null, // decorative; label names the category
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.md))

        LeftTextField(
            value = merchant,
            onValueChange = onMerchantChange,
            label = "Merchant (optional)",
            placeholder = "e.g. Chai Point",
        )
        Spacer(modifier = Modifier.height(spacing.md))
        LeftTextField(
            value = note,
            onValueChange = onNoteChange,
            label = "Note (optional)",
            placeholder = "e.g. Team lunch",
            singleLine = false,
        )
        Spacer(modifier = Modifier.height(spacing.md))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Date",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            LeftTonalButton(text = date.format(DATE_FORMATTER), onClick = onDateClick)
        }
        Spacer(modifier = Modifier.height(spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recurring",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Marks the transaction as recurring. Automatic repeat scheduling arrives with subscriptions in Phase 7.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = isRecurring, onCheckedChange = onRecurringChange)
        }
    }
}
