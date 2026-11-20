package com.example.sortmate.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.sortmate.notifications.worker.BillReminderWorker
import com.example.sortmate.notifications.worker.ChoreReminderWorker
import com.example.sortmate.notifications.worker.QuietHoursWorker
import com.example.sortmate.notifications.worker.SyncWorker
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    const val WORK_BILLS = "sortmate-bills"
    const val WORK_CHORES = "sortmate-chores"
    const val WORK_QUIET = "sortmate-quiet"
    const val WORK_SYNC = "sortmate-sync"

    fun scheduleAll(context: Context) {
        scheduleBills(context, enabled = true)
        scheduleChores(context, enabled = true)
        scheduleSync(context)
    }

    fun scheduleBills(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_BILLS)
            return
        }
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(LocalTime.of(8, 0)), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_BILLS, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleChores(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_CHORES)
            return
        }
        val request = PeriodicWorkRequestBuilder<ChoreReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(LocalTime.of(18, 0)), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_CHORES, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleQuietHours(context: Context, startMinutes: Int, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_QUIET)
            return
        }
        val start = LocalTime.of((startMinutes / 60) % 24, startMinutes % 60)
        val request = PeriodicWorkRequestBuilder<QuietHoursWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(start), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_QUIET, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun delayUntil(time: LocalTime): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(1_000)
    }
}
