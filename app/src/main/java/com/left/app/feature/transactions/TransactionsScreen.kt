package com.left.app.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.left.app.core.designsystem.theme.LeftTheme

/**
 * S10 Transactions placeholder. Phase 3 adds search, filters and the
 * date-grouped list backed by GetTransactions / TransactionRepository.search.
 */
@Composable
fun TransactionsScreen(modifier: Modifier = Modifier) {
    val spacing = LeftTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = "Transactions",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.xxl))
        // Empty state pattern (UX/UI Spec §6) — never a blank screen.
        Text(
            text = "No transactions yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "Add your first expense and start seeing where your money goes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "The full list, search and filters arrive in Phase 3.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
