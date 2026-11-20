package com.example.sortmate.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.domain.model.PresenceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {

    @Query("SELECT * FROM members WHERE householdId = :householdId ORDER BY name COLLATE NOCASE")
    fun observeMembers(householdId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    fun observeMember(id: String): Flow<MemberEntity?>

    @Query("SELECT * FROM members WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MemberEntity?

    @Query("SELECT * FROM members WHERE householdId = :householdId")
    suspend fun getMembers(householdId: String): List<MemberEntity>

    @Upsert
    suspend fun upsert(member: MemberEntity)

    @Upsert
    suspend fun upsertAll(members: List<MemberEntity>)

    @Query(
        """
        UPDATE members
        SET status = :status, updatedAt = :now, isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: String, status: PresenceStatus, now: Long)

    @Query(
        """
        UPDATE members
        SET fcmToken = :token, updatedAt = :now, isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun updateFcmToken(id: String, token: String, now: Long)

    @Query("UPDATE members SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("SELECT * FROM members WHERE isSynced = 0")
    suspend fun getUnsynced(): List<MemberEntity>

    @Query("DELETE FROM members")
    suspend fun clear()
}
