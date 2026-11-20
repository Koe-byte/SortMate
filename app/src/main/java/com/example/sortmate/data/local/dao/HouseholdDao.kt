package com.example.sortmate.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.sortmate.data.local.entity.HouseholdEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {

    @Query("SELECT * FROM households WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<HouseholdEntity?>

    @Query("SELECT * FROM households WHERE id = :id LIMIT 1")
    suspend fun get(id: String): HouseholdEntity?

    @Upsert
    suspend fun upsert(household: HouseholdEntity)

    @Query(
        """
        UPDATE households
        SET quietStartMinutes = :startMinutes,
            quietEndMinutes = :endMinutes,
            updatedAt = :now,
            isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun updateQuietHours(id: String, startMinutes: Int, endMinutes: Int, now: Long)

    @Query(
        """
        UPDATE households
        SET homeLatitude = :latitude,
            homeLongitude = :longitude,
            geofenceRadiusMeters = :radiusMeters,
            updatedAt = :now,
            isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun updateHomeGeofence(
        id: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        now: Long,
    )

    @Query(
        """
        UPDATE households
        SET name = :name, updatedAt = :now, isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun updateName(id: String, name: String, now: Long)

    @Query("UPDATE households SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT * FROM households WHERE isSynced = 0")
    suspend fun getUnsynced(): List<HouseholdEntity>

    @Query("DELETE FROM households")
    suspend fun clear()
}
