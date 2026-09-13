package com.left.app.feature.nativeandroid

import com.left.app.core.domain.BiometricLockState
import com.left.app.core.domain.shouldRequireBiometricUnlock
import javax.inject.Inject

/** Small controller around the pure lock rule; UI prompt wiring can call this safely. */
class BiometricLockController @Inject constructor() {
    fun shouldPrompt(enabled: Boolean, timeoutSeconds: Long, idleSeconds: Long): Boolean =
        shouldRequireBiometricUnlock(BiometricLockState(enabled, timeoutSeconds), idleSeconds)
}
