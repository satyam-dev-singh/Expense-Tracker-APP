package com.left.app.feature.cloud

import androidx.lifecycle.ViewModel
import com.left.app.core.domain.CloudAuthState
import com.left.app.core.domain.SyncState
import com.left.app.core.domain.canRunCloudSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Phase 9 optional cloud shell. No sync runs unless the user explicitly signs in. */
data class CloudSyncUiState(
    val authState: CloudAuthState = CloudAuthState.SIGNED_OUT,
    val syncState: SyncState = SyncState.IDLE,
    val message: String = "Local-first. Cloud sync is optional.",
)

class CloudSyncViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CloudSyncUiState())
    val uiState: StateFlow<CloudSyncUiState> = _uiState.asStateFlow()

    fun onSignInComplete() {
        _uiState.update { it.copy(authState = CloudAuthState.SIGNED_IN, message = "Signed in. Sync can run when you choose.") }
    }

    fun onSyncNow() {
        _uiState.update {
            if (canRunCloudSync(it.authState)) it.copy(syncState = SyncState.RUNNING, message = "Sync started.")
            else it.copy(message = "Sign in before syncing.")
        }
    }

    fun onSyncFinished() {
        _uiState.update { it.copy(syncState = SyncState.IDLE, message = "Sync complete.") }
    }
}
