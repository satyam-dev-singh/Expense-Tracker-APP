package com.left.app.core.domain

/** Pure Phase 8 rules for native Android surfaces. */
data class BiometricLockState(val enabled: Boolean, val timeoutSeconds: Long)

fun shouldRequireBiometricUnlock(state: BiometricLockState, idleSeconds: Long): Boolean =
    state.enabled && idleSeconds >= state.timeoutSeconds

fun enabledShortcutIds(onboardingCompleted: Boolean): List<String> = if (onboardingCompleted) {
    listOf("add_expense", "voice_expense", "open_dashboard")
} else {
    listOf("open_dashboard")
}

fun widgetMoneyLeftText(minorUnits: Long): String = if (minorUnits >= 0) {
    "₹${minorUnits / 100} left"
} else {
    "₹${-minorUnits / 100} over"
}
