package com.example.sortmate.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.sortmate.data.local.entity.ChoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChoreDao {

    @Query(
        """
        SELECT * FROM chores
        WHERE householdId = :householdId AND isDeleted = 0
        ORDER BY isCompleted ASC, dueAt ASC, createdAt DESC
        """,
    )
    fun observeChores(householdId: String): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM chores WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ChoreEntity?

    @Upsert
    suspend fun upsert(chore: ChoreEntity)

    @Upsert
    suspend fun upsertAll(chores: List<ChoreEntity>)

    @Query(
        """
        UPDATE chores
        SET isCompleted = :completed,
            completedAt = :completedAt,
            updatedAt = :now,
            isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun setCompleted(
        id: String,
        completed: Boolean,
        completedAt: Long?,
        now: Long,
    )

    @Query(
        """
        UPDATE chores
        SET isDeleted = 1, updatedAt = :now, isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM chores WHERE householdId = :householdId AND isCompleted = 0 AND dueAt BETWEEN :from AND :to")
    suspend fun getDueBetween(householdId: String, from: Long, to: Long): List<ChoreEntity>

    @Query("SELECT * FROM chores WHERE isSynced = 0")
    suspend fun getUnsynced(): List<ChoreEntity>

    @Query("UPDATE chores SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM chores")
    suspend fun clear()
}
