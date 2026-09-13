package com.left.app.feature.subscriptions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.left.app.R

/**
 * Phase 7 notification delivery surface. The app schedules this worker locally;
 * repository-backed reminder content is planned in pure domain rules and can be
 * expanded once Android build verification is available.
 */
class SubscriptionReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ensureChannel()
        NotificationManagerCompat.from(applicationContext).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Check subscriptions")
                .setContentText("Review renewals and budget alerts in Left.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Subscription reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "subscription_reminders"
        const val NOTIFICATION_ID = 7001
    }
}
