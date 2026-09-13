package com.left.app

import android.app.Application
import com.left.app.core.database.seed.DatabaseSeeder
import com.left.app.core.di.ApplicationScope
import com.left.app.feature.subscriptions.SubscriptionReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class LeftApplication : Application() {

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var subscriptionReminderScheduler: SubscriptionReminderScheduler

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { databaseSeeder.seedDefaultCategories() }
        subscriptionReminderScheduler.scheduleDaily()
    }
}
