package com.wishwell.app.work

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wishwell.app.WishwellApp
import com.wishwell.app.notify.Notifier
import com.wishwell.app.widget.WishwellWidgetUpdater
import java.util.concurrent.TimeUnit

class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WishwellApp ?: return Result.failure()
        return try {
            app.container.notifier.notifyBackupStarted()
            val path = app.container.repository.backupToFile()
            app.container.notifier.notifyBackupCompleted(path)
            WishwellWidgetUpdater.updateAll(app)
            Result.success()
        } catch (t: Throwable) {
            app.container.notifier.notifyBackupFailed(t.message ?: "Unknown error")
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "wishwell_auto_backup"

        fun schedule(
            context: Context,
            frequency: String
        ) {
            // Cancel any existing auto backup work first
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)

            if (frequency == "off") return

            val intervalHours = when (frequency) {
                "6h" -> 6L
                "12h" -> 12L
                "daily" -> 24L
                "weekly" -> 168L
                "monthly" -> 720L
                else -> {
                    // Allow explicit hour values like "6h" / "12h"
                    frequency.removeSuffix("h").toIntOrNull()?.let { it.toLong() } ?: return
                }
            }

            val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
