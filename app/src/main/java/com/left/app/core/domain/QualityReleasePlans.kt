package com.left.app.core.domain

/** Phase 11/12 deterministic QA and release gates. */
data class QaGate(val accessibilityChecked: Boolean, val offlineChecked: Boolean, val migrationChecked: Boolean, val performanceChecked: Boolean, val securityChecked: Boolean)
data class ReleaseGate(val signed: Boolean, val privacyPolicyReady: Boolean, val storeListingReady: Boolean, val debugDisabled: Boolean)

fun qaReady(gate: QaGate): Boolean = gate.accessibilityChecked && gate.offlineChecked && gate.migrationChecked && gate.performanceChecked && gate.securityChecked
fun releaseReady(gate: ReleaseGate): Boolean = gate.signed && gate.privacyPolicyReady && gate.storeListingReady && gate.debugDisabled
fun contrastDelta(foreground: Int, background: Int): Int = kotlin.math.abs(foreground - background)
