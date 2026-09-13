package com.left.app.core.datastore

/**
 * The slice of user preferences that onboarding writes. Kept as an interface so
 * use cases can be unit-tested with in-memory fakes (DataStore itself is a
 * concrete class wired by Hilt).
 */
interface OnboardingPreferences {
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setDefaultCurrencyCode(code: String)
}
