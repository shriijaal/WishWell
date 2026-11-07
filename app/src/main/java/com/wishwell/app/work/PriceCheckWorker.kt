package com.wishwell.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wishwell.app.WishwellApp
import com.wishwell.app.widget.WishwellWidgetUpdater
import java.util.concurrent.TimeUnit

class PriceCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? WishwellApp ?: return Result.failure()
        return try {
            app.container.repository.ensureDefaultCategories()
            app.container.repository.refreshAll()
            WishwellWidgetUpdater.updateAll(app)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}

class WorkScheduler(private val context: Context) {

    fun schedulePeriodic(hours: Int) {
        val request = PeriodicWorkRequestBuilder<PriceCheckWorker>(hours.toLong().coerceAtLeast(1), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
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

    fun scheduleQuickCheck() {
        val request = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "wishwell_price_check"
    }
}
