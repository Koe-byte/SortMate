package com.example.sortmate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chores",
    indices = [Index("householdId"), Index("dueAt")],
)
data class ChoreEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val title: String,
    val assignedMemberId: String? = null,
    val dueAt: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
)
