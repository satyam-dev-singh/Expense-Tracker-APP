package com.left.app.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.model.Category
import com.left.app.core.model.Transaction
import com.left.app.core.model.TransactionType

/**
 * One transaction row, shared by the dashboard (recent) and the transactions
 * list. Shows category icon + name (merchant as fallback title), merchant/note
 * as subtitle, and the signed amount — income uses the income color, expenses
 * stay neutral (calm design direction; color is never the only signal — the
 * sign is always printed too, UX/UI Spec §8).
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    currencyCode: String,
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    val isIncome = transaction.type == TransactionType.INCOME
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LeftIcons.category(category?.iconKey ?: "other"),
            contentDescription = category?.name ?: "Uncategorized",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category?.name ?: transaction.merchant ?: "Uncategorized",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = transaction.merchant?.takeIf { category != null } ?: transaction.note
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(spacing.sm))
        Text(
            text = (if (isIncome) "+" else "−") + transaction.amount.format(currencyCode),
            style = MaterialTheme.typography.titleMedium,
            color = if (isIncome) LeftTheme.extendedColors.income else MaterialTheme.colorScheme.onSurface,
        )
    }
}
