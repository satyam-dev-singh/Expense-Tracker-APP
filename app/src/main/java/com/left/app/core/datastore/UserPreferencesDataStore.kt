package com.left.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Lightweight app preferences backed by DataStore (PRD §4 stack).
 *
 * Only non-financial settings live here (onboarding flag, default currency).
 * Financial records always live in Room. Used by the splash routing decision
 * now and by Phase 2 onboarding.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingPreferences {
    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[Keys.ONBOARDING_COMPLETED] ?: false }
        .distinctUntilChanged()

    val defaultCurrencyCode: Flow<String?> = dataStore.data
        .map { preferences -> preferences[Keys.DEFAULT_CURRENCY_CODE] }
        .distinctUntilChanged()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setDefaultCurrencyCode(code: String) {
        dataStore.edit { preferences -> preferences[Keys.DEFAULT_CURRENCY_CODE] = code }
    }

    /** Wipes all preferences (used by the future "delete local data" flow, FR-12). */
    suspend fun clearAll() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEFAULT_CURRENCY_CODE = stringPreferencesKey("default_currency_code")
    }

    companion object {
        const val FILE_NAME: String = "user_preferences"
    }
}
