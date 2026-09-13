package com.left.app.feature.onboarding

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.component.LeftTextButton
import com.left.app.core.designsystem.theme.LeftTheme

/**
 * S02–S05 Onboarding host (Implementation Plan Phase 2): a five-step flow —
 * Welcome → Currency → Income → Budget → Categories — writing profile, budget,
 * income source and preferences through CompleteOnboarding. Business logic
 * lives in [OnboardingViewModel] / use cases, never in composables.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Completion is a one-way navigation event: the flag is persisted before
    // this fires, so even a process death here still lands on Home next launch.
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished()
    }

    val spacing = LeftTheme.spacing
    Column(modifier = modifier.fillMaxSize().padding(spacing.lg)) {
        LinearProgressIndicator(
            progress = { (state.step.ordinal + 1) / OnboardingStep.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = "Step ${state.step.ordinal + 1} of ${OnboardingStep.entries.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.md))

        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep(
                    name = state.name,
                    nameError = state.nameError,
                    onNameChange = viewModel::onNameChange,
                )
                OnboardingStep.CURRENCY -> CurrencyStep(
                    selectedCode = state.currencyCode,
                    customCode = state.customCurrencyCode,
                    currencyError = state.currencyError,
                    onSelect = viewModel::onCurrencySelected,
                    onCustomChange = viewModel::onCustomCurrencyChange,
                )
                OnboardingStep.INCOME -> IncomeStep(
                    incomeName = state.incomeName,
                    amountInput = state.incomeAmountInput,
                    frequency = state.incomeFrequency,
                    currencyCode = state.currencyCode,
                    amountError = state.incomeError,
                    onNameChange = viewModel::onIncomeNameChange,
                    onAmountChange = viewModel::onIncomeAmountChange,
                    onFrequencyChange = viewModel::onIncomeFrequencyChange,
                )
                OnboardingStep.BUDGET -> BudgetStep(
                    budgetInput = state.budgetInput,
                    currencyCode = state.currencyCode,
                    budgetError = state.budgetError,
                    onBudgetChange = viewModel::onBudgetChange,
                )
                OnboardingStep.CATEGORIES -> CategoriesStep(
                    categories = state.categories,
                    deselectedCategoryIds = state.deselectedCategoryIds,
                    onToggle = viewModel::onCategoryToggle,
                )
            }
        }

        state.saveError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(spacing.sm))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step != OnboardingStep.WELCOME) {
                LeftTextButton(
                    text = "Back",
                    onClick = viewModel::onBack,
                    enabled = !state.saving,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.step == OnboardingStep.INCOME || state.step == OnboardingStep.BUDGET) {
                LeftTextButton(
                    text = "Skip",
                    onClick = viewModel::onSkip,
                    enabled = !state.saving,
                )
                Spacer(modifier = Modifier.width(spacing.sm))
            }
            LeftPrimaryButton(
                text = when {
                    state.saving -> "Saving…"
                    state.step == OnboardingStep.CATEGORIES -> "Finish"
                    state.step == OnboardingStep.WELCOME -> "Get started"
                    else -> "Continue"
                },
                onClick = viewModel::onNext,
                enabled = !state.saving,
            )
        }
    }
}
