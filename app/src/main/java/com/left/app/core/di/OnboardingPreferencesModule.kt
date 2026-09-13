package com.left.app.core.di

import com.left.app.core.datastore.OnboardingPreferences
import com.left.app.core.datastore.UserPreferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the onboarding preferences contract to the DataStore implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingPreferencesModule {

    @Binds
    @Singleton
    abstract fun bindOnboardingPreferences(impl: UserPreferencesDataStore): OnboardingPreferences
}
