package com.left.app.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.left.app.R
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.theme.LeftTheme

/**
 * S07 Add Transaction placeholder. Phase 3 implements the amount-first form
 * (amount → expense/income toggle → category → merchant/note → date → save)
 * on top of AddTransaction use case and LeftAmountField.
 */
@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
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
        Spacer(modifier = Modifier.height(spacing.xl))
        Text(
            text = "Amount-first quick entry arrives in Phase 3.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "The data layer behind it (Room, repositories, AddTransaction with validation) is already in place.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
