package com.example.sortmate.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sortmate.SortMateApplication
import com.example.sortmate.domain.model.PresenceStatus
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null || event.hasError()) {
            Log.w(TAG, "Geofence event failed: ${event?.errorCode}")
            return
        }

        val status = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> PresenceStatus.HOME
            Geofence.GEOFENCE_TRANSITION_EXIT -> PresenceStatus.AWAY
            else -> return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as? SortMateApplication ?: run {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.container
                container.householdRepository.updatePresence(status)
                val session = container.userPreferences.session.first()
                if (session.activityAlerts) {
                    container.notificationHelper.showPresenceUpdate(status)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not persist geofence transition", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}
