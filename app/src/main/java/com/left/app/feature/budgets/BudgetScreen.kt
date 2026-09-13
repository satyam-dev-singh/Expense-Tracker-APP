package com.left.app.feature.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.R
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftAmountField
import com.left.app.core.designsystem.component.LeftCard
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.domain.BudgetStatus
import java.util.Locale

/**
 * S14 Budgets (PRD FR-06, Phase 4): the proactive "safe to spend" surface.
 * Total monthly budget plus optional per-category limits. All state and logic
 * live in [BudgetViewModel]; this composable only renders and forwards events.
 */
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LeftTheme.spacing

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
                text = "Budget",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(spacing.md))

        // Month navigation (Phase 4 month rollover).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = viewModel::onPreviousMonth) {
                Icon(imageVector = LeftIcons.Previous, contentDescription = "Previous month")
            }
            Text(
                text = state.monthLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = viewModel::onNextMonth) {
                Icon(imageVector = LeftIcons.Next, contentDescription = "Next month")
            }
        }

        // Current pace for the selected month (context, not editable).
        state.existingTotalBudget?.let { budget ->
            LeftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(spacing.md)) {
                    LinearProgressIndicator(
                        progress = { ((state.usagePercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Text(
                        text = "${state.monthExpenses.format(state.currencyCode)} spent of ${budget.format(state.currencyCode)}" +
                            (state.usagePercent?.let { " · " + String.format(Locale.US, "%.2f", it) + "%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.status?.let { status ->
                        Text(
                            text = when (status) {
                                BudgetStatus.ON_TRACK -> "On track"
                                BudgetStatus.WARNING -> "Approaching budget limit"
                                BudgetStatus.EXCEEDED -> "Over budget"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when (status) {
                                BudgetStatus.ON_TRACK -> MaterialTheme.colorScheme.onSurfaceVariant
                                BudgetStatus.WARNING -> LeftTheme.extendedColors.warning
                                BudgetStatus.EXCEEDED -> MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.md))
        }

        // Total budget.
        LeftAmountField(
            value = state.totalBudgetInput,
            onValueChange = viewModel::onTotalBudgetChange,
            label = "Total budget (${state.currencyCode})",
            isError = state.totalBudgetError != null,
            supportingText = state.totalBudgetError ?: "Leave empty to remove this month’s budget",
        )
        state.rolloverNotice?.let { notice ->
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        // Per-category limits.
        Text(
            text = "Category limits",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "Optional caps per category. Empty means no cap.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        state.categoryBudgets.forEach { categoryBudget ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = LeftIcons.category(categoryBudget.category.iconKey),
                    contentDescription = categoryBudget.category.name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = categoryBudget.category.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${categoryBudget.spent.format(state.currencyCode)} spent this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(spacing.sm))
                LeftAmountField(
                    value = categoryBudget.input,
                    onValueChange = { viewModel.onCategoryBudgetChange(categoryBudget.category.id, it) },
                    label = "Limit",
                    isError = categoryBudget.error != null,
                    supportingText = categoryBudget.error,
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        state.saveError?.let { error ->
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.saved) {
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = "Saved. The dashboard now reflects this budget.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(spacing.xl))
        LeftPrimaryButton(
            text = if (state.saving) "Saving…" else "Save budget",
            onClick = viewModel::onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.saving,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
    }
}
