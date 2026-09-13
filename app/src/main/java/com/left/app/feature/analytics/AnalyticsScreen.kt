package com.left.app.feature.analytics

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
 * S13 Analytics placeholder. Phase 6 adds category distribution, trends and
 * month-over-month comparison — explained in words first, charts second
 * (PRD §4 principle 4).
 */
@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier) {
    val spacing = LeftTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = "Analytics",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "Understand where your money goes. Arrives in Phase 6.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
