package com.left.app.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftCard
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.component.LeftTonalButton
import com.left.app.core.designsystem.moneyHero
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.domain.BudgetStatus
import com.left.app.feature.transactions.TransactionRow
import java.util.Locale

/**
 * S06 Dashboard (PRD FR-02 + FR-06). Locked visual hierarchy (Master Prompt §8):
 *
 *     MONEY LEFT  (actual: income − expenses)
 *     ↓ spending / budget context (pace status + daily allowance)
 *     ↓ recent activity
 *     ↓ quick action (manual or voice)
 *
 * Budget remaining is always labeled as such and never conflated with money
 * left (PRD §15). All values come from [DashboardViewModel]; this composable
 * contains no business logic.
 */
@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onManageBudgets: () -> Unit,
    onVoiceAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LeftTheme.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.lg))

        if (state.error != null) {
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        // Month navigation (Phase 4 rollover).
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

        // 1. MONEY LEFT — the hero number.
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = if (state.loading) "…" else state.moneyLeft.format(state.currencyCode),
            style = MaterialTheme.typography.moneyHero,
            color = if (state.moneyLeft.isNegative) {
                LeftTheme.extendedColors.warning
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
        Text(
            text = "LEFT THIS MONTH",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        // 2. SPENDING / BUDGET CONTEXT (tap to manage budgets).
        LeftCard(modifier = Modifier.fillMaxWidth(), onClick = onManageBudgets) {
            Column(modifier = Modifier.padding(spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "+" + state.income.format(state.currencyCode),
                            style = MaterialTheme.typography.titleMedium,
                            color = LeftTheme.extendedColors.income,
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Spent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "−" + state.expenses.format(state.currencyCode),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (state.hasBudget) {
                    Spacer(modifier = Modifier.height(spacing.md))
                    LinearProgressIndicator(
                        progress = {
                            ((state.budgetUsagePercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Text(
                        text = buildString {
                            append(state.budgetRemaining?.format(state.currencyCode).orEmpty())
                            append(" budget remaining")
                            state.budgetUsagePercent?.let { usage ->
                                append(" · ")
                                append(String.format(Locale.US, "%.2f", usage))
                                append("% used")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.budgetStatus?.let { status ->
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
                    state.dailyAllowance?.let { allowance ->
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = "Safe to spend about ${allowance.format(state.currencyCode)} per day for the rest of this month.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else if (!state.loading) {
                    Spacer(modifier = Modifier.height(spacing.md))
                    Text(
                        text = "No budget set for ${state.monthLabel}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(spacing.xs))
                    LeftTonalButton(text = "Set a budget", onClick = onManageBudgets)
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.lg))

        // 3. RECENT ACTIVITY
        Text(
            text = "Recent",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        if (!state.loading && state.recentTransactions.isEmpty()) {
            // Empty state pattern (UX/UI Spec §6) — helpful, never blank.
            Text(
                text = "No transactions yet. Add your first expense to see it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.recentTransactions.forEach { transaction ->
                TransactionRow(
                    transaction = transaction,
                    currencyCode = state.currencyCode,
                    category = transaction.categoryId?.let(state.categoriesById::get),
                    onClick = { onTransactionClick(transaction.id) },
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        // 4. QUICK ACTION (manual or voice, FR-03/FR-04)
        Row(modifier = Modifier.fillMaxWidth()) {
            LeftPrimaryButton(
                text = "Add expense",
                onClick = onAddTransaction,
                modifier = Modifier.weight(1f),
                leadingIcon = LeftIcons.Add,
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            LeftTonalButton(
                text = "Voice",
                onClick = onVoiceAdd,
                leadingIcon = LeftIcons.Mic,
            )
        }
        Spacer(modifier = Modifier.height(spacing.lg))
    }
}
