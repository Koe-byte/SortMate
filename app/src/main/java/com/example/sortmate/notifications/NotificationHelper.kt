package com.example.sortmate.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.sortmate.MainActivity
import com.example.sortmate.R
import com.example.sortmate.domain.QuietHours
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.util.formatPeso

class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(NotificationManager::class.java)
        systemManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_BILLS,
                    context.getString(R.string.channel_bills),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Due dates and split totals" },
                NotificationChannel(
                    CHANNEL_CHORES,
                    context.getString(R.string.channel_chores),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Chore shifts and assigned tasks" },
                NotificationChannel(
                    CHANNEL_ACTIVITY,
                    context.getString(R.string.channel_presence),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Geofence and housemate status changes" },
                NotificationChannel(
                    CHANNEL_QUIET,
                    context.getString(R.string.channel_quiet),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Quiet-hour study reminders" },
            ),
        )
    }

    fun showBillReminder(count: Int, totalCents: Long) {
        val body = if (count == 1) {
            "One bill is due soon. Household total: ${formatPeso(totalCents)}."
        } else {
            "$count bills are due soon. Household total: ${formatPeso(totalCents)}."
        }
        notify(NOTIFICATION_BILLS, CHANNEL_BILLS, "Bills need attention", body)
    }

    fun showChoreReminder(count: Int) {
        val body = if (count == 1) {
            "One chore is waiting for a housemate."
        } else {
            "$count chores are waiting for a housemate."
        }
        notify(NOTIFICATION_CHORES, CHANNEL_CHORES, "Chore check-in", body)
    }

    fun showPresenceUpdate(status: PresenceStatus) {
        val body = when (status) {
            PresenceStatus.HOME -> "You are now marked At home. Roommates will see the update."
            PresenceStatus.CAMPUS -> "You are now marked On campus. Roommates will see the update."
            PresenceStatus.AWAY -> "You left your home geofence. Your status changed to Away."
        }
        notify(NOTIFICATION_PRESENCE, CHANNEL_ACTIVITY, "Status updated", body)
    }

    fun showQuietHours(startMinutes: Int, endMinutes: Int) {
        val body = "Quiet hours started. Keep noise down until ${QuietHours.format(endMinutes)} " +
            "(${QuietHours.format(startMinutes)}–${QuietHours.format(endMinutes)})."
        notify(NOTIFICATION_QUIET, CHANNEL_QUIET, "Study mode is on", body)
    }

    fun showRemoteAlert(type: String, title: String, body: String) {
        val channel = when (type) {
            "bill" -> CHANNEL_BILLS
            "chore" -> CHANNEL_CHORES
            "quiet" -> CHANNEL_QUIET
            else -> CHANNEL_ACTIVITY
        }
        notify(NOTIFICATION_REMOTE, channel, title, body)
    }

    private fun notify(id: Int, channel: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val CHANNEL_BILLS = "bill_reminders"
        const val CHANNEL_CHORES = "chore_reminders"
        const val CHANNEL_ACTIVITY = "household_alerts"
        const val CHANNEL_QUIET = "quiet_hours"

        private const val NOTIFICATION_BILLS = 1001
        private const val NOTIFICATION_CHORES = 1002
        private const val NOTIFICATION_PRESENCE = 1003
        private const val NOTIFICATION_QUIET = 1004
        private const val NOTIFICATION_REMOTE = 1005
    }
}
