package com.left.app.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.left.app.core.datastore.UserPreferencesDataStore
import com.left.app.core.designsystem.theme.LeftTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Where the splash screen routes once local state is loaded (UX/UI Spec S01). */
enum class SplashRoute { ONBOARDING, HOME }

@HiltViewModel
class SplashViewModel @Inject constructor(
    userPreferencesDataStore: UserPreferencesDataStore,
) : ViewModel() {

    /** null while the onboarding flag is still loading. */
    val destination: StateFlow<SplashRoute?> = userPreferencesDataStore.onboardingCompleted
        .map { completed -> if (completed) SplashRoute.HOME else SplashRoute.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * S01 Splash: initializes local state and routes to onboarding or Home.
 * Full branding/motion polish is deliberately out of scope for Phase 0.
 */
@Composable
fun SplashScreen(
    onNavigate: (SplashRoute) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        destination?.let(onNavigate)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Left",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(LeftTheme.spacing.sm))
            Text(
                text = "Know what’s left.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
