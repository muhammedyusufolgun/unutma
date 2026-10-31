package com.olgun.unutma

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Best-effort periodic retrieval. Android can defer this work; not instant push. */
class MessageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!InternetMessaging.configured(applicationContext)) return Result.success()
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                InternetMessaging.sync(applicationContext)
            }
            Result.success()
        } catch (e: SecurityException) {
            android.util.Log.e("TimeIkizSync", "Message verification failed; user action required", e)
            Result.failure()
        } catch (e: Exception) {
            android.util.Log.w("TimeIkizSync", "Message sync deferred", e)
            Result.retry()
        }
    }
}

object MessageSyncScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MessageSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "time_ikiz_inbox_sync", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
