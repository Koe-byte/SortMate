package com.example.sortmate.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

class GeofenceManager(private val context: Context) {

    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    suspend fun registerHomeGeofence(
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 100f,
    ): Result<Unit> = register(HOME_GEOFENCE_ID, latitude, longitude, radiusMeters)

    suspend fun registerCampusGeofence(
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 100f,
    ): Result<Unit> = register(CAMPUS_GEOFENCE_ID, latitude, longitude, radiusMeters)

    private suspend fun register(
        requestId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> = runCatching {
        check(hasFineLocation(context)) { "Allow precise location first." }
        check(hasBackgroundLocation(context)) {
            "Choose \"Allow all the time\" in Location settings so the geofence can run while the app is closed."
        }

        val geofence = Geofence.Builder()
            .setRequestId(requestId)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        client.addGeofences(request, geofencePendingIntent()).await()
    }

    suspend fun remove(requestId: String): Result<Unit> = runCatching {
        client.removeGeofences(listOf(requestId)).await()
    }

    suspend fun removeAllGeofences(): Result<Unit> = runCatching {
        client.removeGeofences(geofencePendingIntent()).await()
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(ACTION_GEOFENCE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        const val HOME_GEOFENCE_ID = "sortmate-home"
        const val CAMPUS_GEOFENCE_ID = "sortmate-campus"
        const val ACTION_GEOFENCE = "com.example.sortmate.action.GEOFENCE"
        private const val NOTIFICATION_RESPONSIVENESS_MS = 30_000
    }
}
