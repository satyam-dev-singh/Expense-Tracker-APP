package com.left.app.feature.dashboard

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
import com.left.app.core.designsystem.moneyHero
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.feature.transactions.TransactionRow
import java.util.Locale

/**
 * S06 Dashboard (PRD FR-02). Locked visual hierarchy (Master Prompt §8):
 *
 *     MONEY LEFT  (actual: income − expenses)
 *     ↓ spending / budget context
 *     ↓ recent activity
 *     ↓ quick action
 *
 * Budget remaining is always labeled as such and never conflated with money
 * left (PRD §15). All values come from [DashboardViewModel]; this composable
 * contains no business logic.
 */
@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit,
    onTransactionClick: (String) -> Unit,
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
        Spacer(modifier = Modifier.height(spacing.xl))

        if (state.error != null) {
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        // 1. MONEY LEFT — the hero number.
        Text(
            text = state.monthLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
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

        // 2. SPENDING / BUDGET CONTEXT
        LeftCard(modifier = Modifier.fillMaxWidth()) {
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

        // 4. QUICK ACTION
        LeftPrimaryButton(
            text = "Add expense",
            onClick = onAddTransaction,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = LeftIcons.Add,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
    }
}
