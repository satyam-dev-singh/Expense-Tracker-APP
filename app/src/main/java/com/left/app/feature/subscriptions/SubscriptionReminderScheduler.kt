package com.left.app.feature.subscriptions

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Phase 7 daily reminder scheduler. Delivery worker is intentionally tiny and local-only. */
class SubscriptionReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<SubscriptionReminderWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "subscription_reminders_daily"
    }
}
