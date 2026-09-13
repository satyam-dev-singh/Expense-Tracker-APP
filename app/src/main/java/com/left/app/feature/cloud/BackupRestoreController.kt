package com.left.app.feature.cloud

import com.left.app.core.domain.BackupManifest
import com.left.app.core.domain.isRestorableBackup
import javax.inject.Inject

/** Phase 9 backup/restore guard. Actual export/import remains explicit and user-controlled. */
class BackupRestoreController @Inject constructor() {
    fun canRestore(encrypted: Boolean, rowCount: Int): Boolean =
        isRestorableBackup(BackupManifest(encrypted, rowCount))
}
