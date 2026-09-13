package com.left.app.feature.qa

import com.left.app.core.domain.QaGate
import com.left.app.core.domain.qaReady

/** Phase 11 QA checklist helper. */
class QaChecklist {
    fun isReady(gate: QaGate): Boolean = qaReady(gate)
}
