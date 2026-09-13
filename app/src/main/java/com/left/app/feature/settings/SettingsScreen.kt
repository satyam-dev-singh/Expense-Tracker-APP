package com.left.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.left.app.core.designsystem.component.LeftCard
import com.left.app.core.designsystem.theme.LeftTheme

@Composable
fun SettingsScreen(onSubscriptions: () -> Unit = {}, modifier: Modifier = Modifier) {
    val spacing = LeftTheme.spacing
    Column(modifier = modifier.fillMaxSize().padding(spacing.md)) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(spacing.lg))
        LeftCard(onClick = onSubscriptions) {
            Column(modifier = Modifier.padding(spacing.md)) {
                Text("Subscriptions & reminders", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Manage recurring spends and renewal nudges", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(spacing.md))
        listOf("Profile" to "Phase 2", "Currency" to "Phase 2", "Categories" to "Phase 3", "Data export" to "Later", "About" to "Later").forEach { (section, phase) -> Text(section, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface); Text("Arrives in $phase", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.height(spacing.md)) }
    }
}
