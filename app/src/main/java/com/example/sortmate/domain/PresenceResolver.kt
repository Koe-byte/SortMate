package com.example.sortmate.domain

import com.example.sortmate.domain.model.PresenceStatus

/** The two places SortMate tracks with Android geofences. */
enum class GeofenceArea {
    HOME,
    CAMPUS,
}

/**
 * Pure state machine for overlapping geofences.
 *
 * Android delivers one transition event at a time and does not tell us which
 * other fences the device is inside. Resolving against the current status keeps
 * the result correct for the common cases:
 *
 * - entering home always wins;
 * - leaving home only marks you away if campus is not the current status;
 * - leaving campus only marks you away if home is not the current status.
 */
object PresenceResolver {

    fun resolve(
        current: PresenceStatus,
        entered: Set<GeofenceArea> = emptySet(),
        exited: Set<GeofenceArea> = emptySet(),
    ): PresenceStatus {
        var next = current

        when {
            GeofenceArea.HOME in entered -> next = PresenceStatus.HOME
            GeofenceArea.CAMPUS in entered -> next = PresenceStatus.CAMPUS
        }

        if (GeofenceArea.HOME in exited && next == PresenceStatus.HOME) {
            next = if (GeofenceArea.CAMPUS in entered) {
                PresenceStatus.CAMPUS
            } else {
                PresenceStatus.AWAY
            }
        }

        if (GeofenceArea.CAMPUS in exited && next == PresenceStatus.CAMPUS) {
            next = if (GeofenceArea.HOME in entered) {
                PresenceStatus.HOME
            } else {
                PresenceStatus.AWAY
            }
        }

        return next
    }
}
