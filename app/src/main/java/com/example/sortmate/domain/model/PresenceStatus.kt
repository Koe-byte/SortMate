package com.example.sortmate.domain.model

/** Presence is updated by the geofence receiver or manually from the household screen. */
enum class PresenceStatus(val label: String) {
    HOME("At home"),
    CAMPUS("On campus"),
    AWAY("Away");

    companion object {
        fun from(value: String?): PresenceStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AWAY
    }
}
