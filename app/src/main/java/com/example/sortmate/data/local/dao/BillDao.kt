package com.example.sortmate.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.sortmate.data.local.entity.BillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query(
        """
        SELECT * FROM bills
        WHERE householdId = :householdId AND isDeleted = 0
        ORDER BY isSettled ASC, dueAt ASC, createdAt DESC
        """,
    )
    fun observeBills(householdId: String): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BillEntity?

    @Upsert
    suspend fun upsert(bill: BillEntity)

    @Upsert
    suspend fun upsertAll(bills: List<BillEntity>)

    @Query(
        """
        UPDATE bills
        SET isSettled = :settled, updatedAt = :now, isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun setSettled(id: String, settled: Boolean, now: Long)

    @Query(
        """
        UPDATE bills
        SET isDeleted = 1, updatedAt = :now, isSynced = 0
        WHERE id = :id
        """,
    )
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM bills WHERE householdId = :householdId AND isSettled = 0 AND dueAt BETWEEN :from AND :to")
    suspend fun getDueBetween(householdId: String, from: Long, to: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE isSynced = 0")
    suspend fun getUnsynced(): List<BillEntity>

    @Query("UPDATE bills SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM bills")
    suspend fun clear()
}
