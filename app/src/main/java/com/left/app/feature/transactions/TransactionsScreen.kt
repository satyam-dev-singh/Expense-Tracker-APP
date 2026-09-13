package com.left.app.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftAmountField
import com.left.app.core.designsystem.component.LeftTextField
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.model.Category
import com.left.app.core.model.TransactionType

/**
 * S10 Transactions (PRD FR-11): month navigation, text search, type/category/
 * amount-range filters, grouped by day. All logic lives in
 * [TransactionsViewModel]; this composable only renders state.
 */
@Composable
fun TransactionsScreen(
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LeftTheme.spacing
    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = spacing.md)) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = "Transactions",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.sm))

        // Month navigation is hidden while a text query searches all time.
        if (!state.searchingAllTime) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = viewModel::onPreviousMonth) {
                    Icon(
                        imageVector = LeftIcons.Previous,
                        contentDescription = "Previous month",
                    )
                }
                Text(
                    text = state.monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = viewModel::onNextMonth) {
                    Icon(
                        imageVector = LeftIcons.Next,
                        contentDescription = "Next month",
                    )
                }
            }
        }

        LeftTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = "Search",
            placeholder = "Merchant or note",
        )
        Spacer(modifier = Modifier.height(spacing.xs))

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            FilterChip(
                selected = state.typeFilter == null,
                onClick = { viewModel.onTypeFilterChange(null) },
                label = { Text("All") },
            )
            FilterChip(
                selected = state.typeFilter == TransactionType.EXPENSE,
                onClick = { viewModel.onTypeFilterChange(TransactionType.EXPENSE) },
                label = { Text("Expenses") },
            )
            FilterChip(
                selected = state.typeFilter == TransactionType.INCOME,
                onClick = { viewModel.onTypeFilterChange(TransactionType.INCOME) },
                label = { Text("Income") },
            )
        }
        Spacer(modifier = Modifier.height(spacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryFilterDropdown(
                categories = state.categories,
                selectedId = state.categoryFilterId,
                onSelect = viewModel::onCategoryFilterChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            LeftAmountField(
                value = state.amountMinInput,
                onValueChange = viewModel::onAmountMinChange,
                label = "Min",
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            LeftAmountField(
                value = state.amountMaxInput,
                onValueChange = viewModel::onAmountMaxChange,
                label = "Max",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(spacing.sm))

        state.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(spacing.sm))
        }

        when {
            state.loading -> Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.groups.isEmpty() -> {
                Spacer(modifier = Modifier.height(spacing.lg))
                Text(
                    text = if (state.searchingAllTime) {
                        "No matches for “${state.query}”."
                    } else {
                        "No transactions in ${state.monthLabel} yet."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = if (state.searchingAllTime) {
                        "Try a different search or clear the filters."
                    } else {
                        "Add an expense and it will appear here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                state.groups.forEach { group ->
                    item(key = "header-${group.date}") {
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = spacing.xs),
                        )
                    }
                    items(group.transactions, key = { it.id }) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            currencyCode = state.currencyCode,
                            category = transaction.categoryId?.let(categoriesById::get),
                            onClick = { onTransactionClick(transaction.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterDropdown(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name
    Box(modifier = modifier) {
        FilterChip(
            selected = selectedId != null,
            onClick = { expanded = true },
            label = { Text(selectedName ?: "All categories") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All categories") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
