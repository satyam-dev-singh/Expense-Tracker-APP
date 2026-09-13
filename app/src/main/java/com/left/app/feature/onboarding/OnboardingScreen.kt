package com.left.app.feature.onboarding

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
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.theme.LeftTheme

/**
 * S02–S05 Onboarding placeholder.
 *
 * Phase 2 implements the real flow (welcome → currency → income → budget →
 * categories → completion) writing UserProfile/MonthlyBudget via repositories
 * and setting the onboarding flag in UserPreferencesDataStore.
 *
 * The "Get started" button below is a TEMPORARY navigation affordance so the
 * skeleton can reach Home on a clean install.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.lg),
    ) {
        Spacer(modifier = Modifier.height(spacing.xxl))
        Text(
            text = "Welcome to Left",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "Add a transaction in seconds. Always know what’s left.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = "The setup flow (name, currency, income, budget) arrives in Phase 2.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        // TODO(phase-2): replace with the completed onboarding flow.
        LeftPrimaryButton(
            text = "Get started",
            onClick = onFinished,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(spacing.lg))
    }
}
