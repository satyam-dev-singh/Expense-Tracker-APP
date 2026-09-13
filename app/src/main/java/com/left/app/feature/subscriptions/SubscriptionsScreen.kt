package com.left.app.feature.subscriptions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.core.designsystem.component.LeftAmountField
import com.left.app.core.designsystem.component.LeftCard
import com.left.app.core.designsystem.component.LeftPrimaryButton
import com.left.app.core.designsystem.component.LeftTextField
import com.left.app.core.designsystem.theme.LeftTheme
import com.left.app.core.model.BillingCycle

@Composable
fun SubscriptionsScreen(modifier: Modifier = Modifier, viewModel: SubscriptionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(); val sp = LeftTheme.spacing
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(sp.md)) {
        Spacer(Modifier.height(sp.lg)); Text("Subscriptions", style = MaterialTheme.typography.headlineMedium); Text("Recurring money, before it surprises you.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(sp.md))
        LeftCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(sp.md)) { Text("Projected monthly", style = MaterialTheme.typography.titleMedium); Text(state.projectedMonthly.format(state.currencyCode), style = MaterialTheme.typography.headlineSmall); if (state.upcoming.isNotEmpty()) Text("Due in 7 days: " + state.upcoming.joinToString { it.name }, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Spacer(Modifier.height(sp.md)); Text("Add subscription", style = MaterialTheme.typography.titleMedium)
        LeftTextField(value = state.form.name, onValueChange = viewModel::onNameChange, label = "Name"); Spacer(Modifier.height(sp.sm))
        LeftAmountField(value = state.form.amount, onValueChange = viewModel::onAmountChange, label = "Amount (${state.currencyCode})"); Spacer(Modifier.height(sp.sm))
        Row { BillingCycle.entries.forEach { cycle -> AssistChip(onClick = { viewModel.onCycleChange(cycle) }, label = { Text(cycle.name.lowercase().replaceFirstChar(Char::uppercase)) }); Spacer(Modifier.width(sp.xs)) } }
        Spacer(Modifier.height(sp.sm)); FilterChip(selected = state.form.reminder, onClick = { viewModel.onReminderChange(!state.form.reminder) }, label = { Text("Reminder") })
        state.form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; Spacer(Modifier.height(sp.sm)); LeftPrimaryButton(text = "Save subscription", onClick = viewModel::onSave, modifier = Modifier.fillMaxWidth())
        if (state.saved) Text("Saved.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(sp.lg)); Text("Active list", style = MaterialTheme.typography.titleMedium)
        state.subscriptions.forEach { sub -> LeftCard(Modifier.fillMaxWidth().padding(vertical = sp.xs)) { Column(Modifier.padding(sp.md)) { Text(sub.name, style = MaterialTheme.typography.titleMedium); Text(sub.amount.format(state.currencyCode) + " · " + sub.billingCycle.name.lowercase() + " · next " + sub.nextBillingDate, color = MaterialTheme.colorScheme.onSurfaceVariant); Row { TextButton(onClick = { viewModel.onMarkPaid(sub) }) { Text("Mark paid") }; TextButton(onClick = { viewModel.onToggleActive(sub.id, !sub.active) }) { Text(if (sub.active) "Pause" else "Resume") }; TextButton(onClick = { viewModel.onDelete(sub.id) }) { Text("Delete") } } } } }
    }
}
