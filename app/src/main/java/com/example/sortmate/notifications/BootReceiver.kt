package com.example.sortmate.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sortmate.SortMateApplication
import com.example.sortmate.location.hasBackgroundLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderScheduler.scheduleAll(context)

        val pendingResult = goAsync()
        val app = context.applicationContext as? SortMateApplication ?: run {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.container
                val session = container.userPreferences.session.first()
                val householdId = session.householdId ?: return@launch
                val household = container.householdRepository.getHousehold(householdId) ?: return@launch
                val latitude = household.homeLatitude ?: return@launch
                val longitude = household.homeLongitude ?: return@launch
                if (hasBackgroundLocation(context)) {
                    container.geofenceManager.registerHomeGeofence(
                        latitude = latitude,
                        longitude = longitude,
                        radiusMeters = household.geofenceRadiusMeters,
                    )
                }
                ReminderScheduler.scheduleQuietHours(
                    context,
                    household.quietStartMinutes,
                    session.quietHoursAlerts,
                )
            } catch (error: Exception) {
                Log.w("BootReceiver", "Could not restore background features", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
