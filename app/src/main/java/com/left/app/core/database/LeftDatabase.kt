package com.left.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.left.app.core.database.dao.CategoryDao
import com.left.app.core.database.dao.IncomeSourceDao
import com.left.app.core.database.dao.MonthlyBudgetDao
import com.left.app.core.database.dao.SubscriptionDao
import com.left.app.core.database.dao.TransactionDao
import com.left.app.core.database.dao.UserProfileDao
import com.left.app.core.database.entity.CategoryEntity
import com.left.app.core.database.entity.IncomeSourceEntity
import com.left.app.core.database.entity.MonthlyBudgetEntity
import com.left.app.core.database.entity.SubscriptionEntity
import com.left.app.core.database.entity.TransactionEntity
import com.left.app.core.database.entity.UserProfileEntity

/**
 * Left local database — the single source of truth for the offline-first MVP
 * (PRD §22). All reads/writes flow through repositories; UI never touches DAOs
 * directly (PRD §13).
 *
 * Migrations: version 1 is the baseline. Schema JSON is exported to
 * `app/schemas` so future migrations (Room `Migration` classes + migration
 * tests) can be reviewed. Destructive fallback is intentionally NOT enabled —
 * silent data loss is unacceptable for financial data (see DATABASE.md).
 */
@Database(
    entities = [
        UserProfileEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        SubscriptionEntity::class,
        IncomeSourceEntity::class,
        MonthlyBudgetEntity::class,
    ],
    version = LeftDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LeftDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun incomeSourceDao(): IncomeSourceDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao

    companion object {
        const val VERSION = 1
        const val DATABASE_NAME = "left.db"
    }
}
