package com.example.sortmate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sortmate.domain.model.PresenceStatus

@Entity(
    tableName = "members",
    indices = [Index("householdId")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val role: String = "Roommate",
    val status: PresenceStatus = PresenceStatus.AWAY,
    val fcmToken: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
)
