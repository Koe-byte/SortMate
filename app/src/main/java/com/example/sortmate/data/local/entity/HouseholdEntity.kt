package com.example.sortmate.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val joinCode: String,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 6 * 60,
    val geofenceRadiusMeters: Float = 100f,
    val homeLatitude: Double? = null,
    val homeLongitude: Double? = null,
    val campusLatitude: Double? = null,
    val campusLongitude: Double? = null,
    @ColumnInfo(defaultValue = "100") val campusRadiusMeters: Float = 100f,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
)
