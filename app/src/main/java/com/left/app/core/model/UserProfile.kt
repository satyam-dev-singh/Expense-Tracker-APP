package com.left.app.core.model

import java.time.Instant

/**
 * Domain model for the local user profile.
 *
 * The MVP is a single-profile, on-device app: the profile row uses the constant
 * id "local_user" (see UserProfileRepository). Multi-account support would only
 * arrive with cloud sync (Phase 9).
 */
data class UserProfile(
    val id: String,
    val name: String,
    val currencyCode: String,
    val locale: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
