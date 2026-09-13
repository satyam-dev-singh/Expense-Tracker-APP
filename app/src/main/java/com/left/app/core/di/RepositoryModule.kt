package com.left.app.core.di

import com.left.app.core.data.CategoryRepository
import com.left.app.core.data.IncomeSourceRepository
import com.left.app.core.data.MonthlyBudgetRepository
import com.left.app.core.data.RoomCategoryRepository
import com.left.app.core.data.RoomIncomeSourceRepository
import com.left.app.core.data.RoomMonthlyBudgetRepository
import com.left.app.core.data.RoomSubscriptionRepository
import com.left.app.core.data.RoomTransactionRepository
import com.left.app.core.data.RoomUserProfileRepository
import com.left.app.core.data.SubscriptionRepository
import com.left.app.core.data.TransactionRepository
import com.left.app.core.data.UserProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds repository interfaces to their Room implementations. Domain/UI code
 * depends only on the interfaces (PRD §13), keeping Room an implementation
 * detail that can be replaced in tests with fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(impl: RoomUserProfileRepository): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: RoomSubscriptionRepository): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindIncomeSourceRepository(impl: RoomIncomeSourceRepository): IncomeSourceRepository

    @Binds
    @Singleton
    abstract fun bindMonthlyBudgetRepository(impl: RoomMonthlyBudgetRepository): MonthlyBudgetRepository
}
