package com.left.app.feature.release

import com.left.app.core.domain.ReleaseGate
import com.left.app.core.domain.releaseReady

/** Phase 12 release checklist helper. */
class ReleaseChecklist {
    fun isReady(gate: ReleaseGate): Boolean = releaseReady(gate)
}
