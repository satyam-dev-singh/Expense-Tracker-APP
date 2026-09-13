package com.left.app.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Offline-first (PRD §22): Room is the source of truth and no network access is
 * required for any Phase 0–1 functionality. This package intentionally contains
 * only this contract — a stable seam for the cloud sync phase (Phase 9, see
 * Technical Architecture §8: Room → Sync Queue → Network → Server).
 *
 * No implementation ships yet; none is needed until cloud sync exists.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
