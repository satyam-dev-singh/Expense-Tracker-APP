package com.left.app.feature.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftCard
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.moneyHero
import com.left.app.core.designsystem.theme.LeftTheme

/**
 * S06 Dashboard placeholder. The visual hierarchy is already locked in
 * (Master Prompt §8):
 *
 *     MONEY LEFT
 *     ↓ spending / budget context
 *     ↓ recent activity
 *     ↓ quick action
 *
 * Phase 3 wires this to ViewModels and the calculation use cases
 * (CalculateRemainingMoney, CalculateBudgetRemaining, observeRecent).
 * No business logic lives in this composable.
 */
@Composable
fun DashboardScreen(
    onAddTransaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.xl))

        // 1. MONEY LEFT
        Text(
            text = "This month",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        // TODO(phase-3): bind to CalculateRemainingMoney via DashboardViewModel.
        Text(
            text = "₹ —",
            style = MaterialTheme.typography.moneyHero,
            color = MaterialTheme.colorScheme.onBackground,
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
                Text(
                    text = "Income and spending summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = "Calculated from your transactions in Phase 3. Budget progress arrives in Phase 4.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        Text(
            text = "Transactions you add will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

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
