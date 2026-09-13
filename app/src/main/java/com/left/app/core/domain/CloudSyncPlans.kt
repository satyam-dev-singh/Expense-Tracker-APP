package com.left.app.core.domain

/** Phase 9 optional cloud rules. Local Room remains the source of truth unless the user signs in. */
enum class CloudAuthState { SIGNED_OUT, SIGNED_IN }
enum class SyncState { IDLE, RUNNING, CONFLICT, ERROR }

data class BackupManifest(
    val encrypted: Boolean,
    val rowCount: Int,
)

fun canRunCloudSync(authState: CloudAuthState): Boolean = authState == CloudAuthState.SIGNED_IN

fun resolveLatestUpdate(localUpdatedAt: String, remoteUpdatedAt: String): String =
    if (localUpdatedAt >= remoteUpdatedAt) localUpdatedAt else remoteUpdatedAt

fun isRestorableBackup(manifest: BackupManifest): Boolean =
    manifest.encrypted && manifest.rowCount >= 0
