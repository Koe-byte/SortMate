package com.example.sortmate.notifications.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sortmate.SortMateApplication
import com.example.sortmate.util.daysFromNow
import kotlinx.coroutines.flow.first

class BillReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SortMateApplication ?: return Result.success()
        val container = app.container
        val session = container.userPreferences.session.first()
        if (!session.billReminders) return Result.success()
        val householdId = session.householdId ?: return Result.success()

        val from = daysFromNow(0)
        val to = System.currentTimeMillis() + 86_400_000L
        val due = container.database.billDao().getDueBetween(householdId, from, to)
        if (due.isNotEmpty()) {
            container.notificationHelper.showBillReminder(
                count = due.size,
                totalCents = due.sumOf { it.totalCents },
            )
        }
        return Result.success()
    }
}
