package com.left.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftAmountField
import com.left.app.core.designsystem.component.LeftTextField
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.model.Category
import com.left.app.core.model.IncomeFrequency

/**
 * The five onboarding step contents (UX/UI Spec S02–S05). Stateless:
 * state flows down from [OnboardingScreen], events flow up.
 */

@Composable
internal fun WelcomeStep(
    name: String,
    nameError: String?,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(modifier = modifier) {
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
        Spacer(modifier = Modifier.height(spacing.xl))
        LeftTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Your name",
            placeholder = "e.g. Satyam",
            supportingText = nameError,
            isError = nameError != null,
        )
    }
}

/** Currencies offered as one-tap choices; any other ISO 4217 code via the field. */
private val CommonCurrencies = listOf("INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "SGD", "AED")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CurrencyStep(
    selectedCode: String,
    customCode: String,
    currencyError: String?,
    onSelect: (String) -> Unit,
    onCustomChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(modifier = modifier) {
        Text(
            text = "Your currency",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "Used for all amounts and budgets. You can change it later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            CommonCurrencies.forEach { code ->
                FilterChip(
                    selected = selectedCode == code && customCode.isEmpty(),
                    onClick = { onSelect(code) },
                    label = { Text(code) },
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.md))
        LeftTextField(
            value = customCode,
            onValueChange = onCustomChange,
            label = "Custom code (optional)",
            placeholder = "e.g. CHF",
            supportingText = currencyError ?: "3-letter ISO 4217 code",
            isError = currencyError != null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IncomeStep(
    incomeName: String,
    amountInput: String,
    frequency: IncomeFrequency,
    currencyCode: String,
    amountError: String?,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onFrequencyChange: (IncomeFrequency) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(modifier = modifier) {
        Text(
            text = "Recurring income",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "Optional — like a monthly salary. It helps Left show what’s left each month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        LeftTextField(
            value = incomeName,
            onValueChange = onNameChange,
            label = "Income name",
            placeholder = "Salary",
        )
        Spacer(modifier = Modifier.height(spacing.md))
        LeftAmountField(
            value = amountInput,
            onValueChange = onAmountChange,
            label = "Amount ($currencyCode)",
            isError = amountError != null,
            supportingText = amountError,
        )
        Spacer(modifier = Modifier.height(spacing.md))
        Text(
            text = "Frequency",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            IncomeFrequency.entries.forEach { frequencyOption ->
                FilterChip(
                    selected = frequency == frequencyOption,
                    onClick = { onFrequencyChange(frequencyOption) },
                    label = { Text(frequencyLabel(frequencyOption)) },
                )
            }
        }
    }
}

@Composable
internal fun BudgetStep(
    budgetInput: String,
    currencyCode: String,
    budgetError: String?,
    onBudgetChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(modifier = modifier) {
        Text(
            text = "Monthly budget",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "Optional. Left always shows budget remaining separately from your actual money left — they are different things.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        LeftAmountField(
            value = budgetInput,
            onValueChange = onBudgetChange,
            label = "Budget ($currencyCode)",
            isError = budgetError != null,
            supportingText = budgetError,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoriesStep(
    categories: List<Category>,
    deselectedCategoryIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LeftTheme.spacing
    Column(modifier = modifier) {
        Text(
            text = "Your categories",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "Tap any you don’t need — hidden ones are archived, never deleted. You can customize later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        if (categories.isEmpty()) {
            Text(
                text = "The ten default categories are created automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category.id !in deselectedCategoryIds,
                        onClick = { onToggle(category.id) },
                        label = { Text(category.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = LeftIcons.category(category.iconKey),
                                contentDescription = null, // decorative; the label names the category
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun frequencyLabel(frequency: IncomeFrequency): String = when (frequency) {
    IncomeFrequency.WEEKLY -> "Weekly"
    IncomeFrequency.BIWEEKLY -> "Every 2 weeks"
    IncomeFrequency.MONTHLY -> "Monthly"
    IncomeFrequency.QUARTERLY -> "Quarterly"
    IncomeFrequency.YEARLY -> "Yearly"
    IncomeFrequency.ONE_TIME -> "One-time"
}
