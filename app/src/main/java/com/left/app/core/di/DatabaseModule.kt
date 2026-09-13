package com.left.app.core.di

import android.content.Context
import androidx.room.Room
import com.left.app.core.database.LeftDatabase
import com.left.app.core.database.dao.CategoryDao
import com.left.app.core.database.dao.IncomeSourceDao
import com.left.app.core.database.dao.MonthlyBudgetDao
import com.left.app.core.database.dao.SubscriptionDao
import com.left.app.core.database.dao.TransactionDao
import com.left.app.core.database.dao.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLeftDatabase(@ApplicationContext context: Context): LeftDatabase =
        Room.databaseBuilder(context, LeftDatabase::class.java, LeftDatabase.DATABASE_NAME)
            // No fallbackToDestructiveMigration(): silent loss of financial data is
            // unacceptable. Schema upgrades ship as explicit Migration classes with
            // migration tests (see DATABASE.md).
            .build()

    @Provides
    fun provideUserProfileDao(database: LeftDatabase): UserProfileDao = database.userProfileDao()

    @Provides
    fun provideCategoryDao(database: LeftDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: LeftDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideSubscriptionDao(database: LeftDatabase): SubscriptionDao = database.subscriptionDao()

    @Provides
    fun provideIncomeSourceDao(database: LeftDatabase): IncomeSourceDao = database.incomeSourceDao()

    @Provides
    fun provideMonthlyBudgetDao(database: LeftDatabase): MonthlyBudgetDao = database.monthlyBudgetDao()
}
