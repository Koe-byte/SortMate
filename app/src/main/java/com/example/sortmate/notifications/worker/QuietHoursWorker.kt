package com.example.sortmate.notifications.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sortmate.SortMateApplication
import kotlinx.coroutines.flow.first

class QuietHoursWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SortMateApplication ?: return Result.success()
        val container = app.container
        val session = container.userPreferences.session.first()
        if (!session.quietHoursAlerts) return Result.success()
        val householdId = session.householdId ?: return Result.success()

        val household = container.database.householdDao().get(householdId) ?: return Result.success()
        container.notificationHelper.showQuietHours(
            household.quietStartMinutes,
            household.quietEndMinutes,
        )
        return Result.success()
    }
}
