package com.left.app.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.left.app.core.designsystem.LeftIcons
import com.left.app.core.designsystem.component.LeftCard
import com.left.app.core.designsystem.theme.LeftTheme
import java.util.Locale

@Composable
fun AnalyticsScreen(modifier: Modifier = Modifier, viewModel: AnalyticsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LeftTheme.spacing
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.md)) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = viewModel::onPreviousMonth) { Icon(LeftIcons.Previous, contentDescription = "Previous month") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Analytics", style = MaterialTheme.typography.headlineMedium); Text(state.monthLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = viewModel::onNextMonth) { Icon(LeftIcons.Next, contentDescription = "Next month") }
        }
        Spacer(modifier = Modifier.height(spacing.md))
        if (state.loading) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); return@Column }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); return@Column }
        LeftCard(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(spacing.md)) { Text("In words", style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(spacing.xs)); Text(state.insightSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Spacer(modifier = Modifier.height(spacing.md))
        SectionTitle("Category distribution")
        if (state.categoryDistribution.isEmpty()) Text("No expenses yet for this month.", color = MaterialTheme.colorScheme.onSurfaceVariant) else state.categoryDistribution.forEach { row -> AnalyticsBarRow(row.categoryName, row.amount.format(state.currencyCode), (row.percent / 100.0).toFloat().coerceIn(0f, 1f), String.format(Locale.US, "%.2f%%", row.percent)) }
        Spacer(modifier = Modifier.height(spacing.md))
        SectionTitle("Month comparison")
        val displayDelta = if (state.monthOverMonthDelta.isNegative) (-state.monthOverMonthDelta).format(state.currencyCode) else state.monthOverMonthDelta.format(state.currencyCode)
        val sign = if (state.monthOverMonthDelta.isNegative) "−" else if (state.monthOverMonthDelta.isPositive) "+" else ""
        Text("$sign$displayDelta vs previous month" + (state.monthOverMonthPercent?.let { " · " + String.format(Locale.US, "%.2f%%", it) } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(spacing.md))
        SectionTitle("Trends")
        val maxExpense = state.trends.maxOfOrNull { it.expenses.minorUnits } ?: 0L
        state.trends.forEach { row -> AnalyticsBarRow(row.monthLabel, row.expenses.format(state.currencyCode), trendProgress(row.expenses.minorUnits, maxExpense), "Left ${row.moneyLeft.format(state.currencyCode)}") }
        Spacer(modifier = Modifier.height(spacing.md))
        LeftCard(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(spacing.md)) { Text("Recurring total", style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(spacing.xs)); Text(state.recurringTotal.format(state.currencyCode), style = MaterialTheme.typography.headlineSmall); Text("Marked recurring expenses plus active subscriptions, projected monthly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Spacer(modifier = Modifier.height(spacing.lg))
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(LeftTheme.spacing.sm)) }
@Composable private fun AnalyticsBarRow(label: String, value: String, progress: Float, detail: String) {
    val spacing = LeftTheme.spacing
    Column(modifier = Modifier.padding(vertical = spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }
        Spacer(modifier = Modifier.height(spacing.xs))
        Box(modifier = Modifier.fillMaxWidth().height(spacing.sm).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)) { Box(modifier = Modifier.fillMaxWidth(progress).height(spacing.sm).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)) }
        Spacer(modifier = Modifier.height(spacing.xs)); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
private fun trendProgress(value: Long, max: Long): Float = if (max <= 0L) 0f else (value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
