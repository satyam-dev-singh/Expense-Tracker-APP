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
import com.left.app.core.designsystem.theme.LeftTheme

/**
 * S15 Settings placeholder. The sections below map to the UX/UI Spec and are
 * implemented in their own phases (profile/currency in Phase 2, categories in
 * Phase 3, notifications in Phase 7, export in later phases).
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val spacing = LeftTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md),
    ) {
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(spacing.lg))

        listOf(
            "Profile" to "Phase 2",
            "Currency" to "Phase 2",
            "Categories" to "Phase 3",
            "Notifications" to "Phase 7",
            "Data export" to "Later",
            "About" to "Later",
        ).forEach { (section, phase) ->
            Text(
                text = section,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Arrives in $phase",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.md))
        }
    }
}
