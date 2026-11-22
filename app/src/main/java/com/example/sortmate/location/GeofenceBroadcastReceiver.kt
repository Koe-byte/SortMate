package com.example.sortmate.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.sortmate.SortMateApplication
import com.example.sortmate.domain.GeofenceArea
import com.example.sortmate.domain.PresenceResolver
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.util.evaluationMark
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

        val entered = mutableSetOf<GeofenceArea>()
        val exited = mutableSetOf<GeofenceArea>()
        event.triggeringGeofences.orEmpty().forEach { geofence ->
            val area = when (geofence.requestId) {
                GeofenceManager.HOME_GEOFENCE_ID -> GeofenceArea.HOME
                GeofenceManager.CAMPUS_GEOFENCE_ID -> GeofenceArea.CAMPUS
                else -> null
            } ?: return@forEach

            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> entered += area
                Geofence.GEOFENCE_TRANSITION_EXIT -> exited += area
            }
        }

        if (entered.isEmpty() && exited.isEmpty()) return

        evaluationMark("geofence_event entered=$entered exited=$exited")

        val pendingResult = goAsync()
        val app = context.applicationContext as? SortMateApplication ?: run {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.container
                val current = container.householdRepository.getCurrentMember()?.status
                    ?: PresenceStatus.AWAY
                val next = PresenceResolver.resolve(current, entered, exited)
                if (next != current) {
                    container.householdRepository.updatePresence(next)
                    val session = container.userPreferences.session.first()
                    if (session.activityAlerts) {
                        container.notificationHelper.showPresenceUpdate(next)
                    }
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
