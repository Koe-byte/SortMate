package com.example.sortmate.notifications.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sortmate.SortMateApplication

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SortMateApplication ?: return Result.success()
        return runCatching {
            val container = app.container
            container.householdRepository.syncPending()
            container.billRepository.syncPending()
            container.choreRepository.syncPending()
            container.householdRepository.refreshFromRemote()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
