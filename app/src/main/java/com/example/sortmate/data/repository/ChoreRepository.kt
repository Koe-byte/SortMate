package com.example.sortmate.data.repository

import com.example.sortmate.data.local.dao.ChoreDao
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChoreRepository(
    private val choreDao: ChoreDao,
    private val remote: FirestoreDataSource,
) {

    fun observeChores(householdId: String): Flow<List<ChoreEntity>> =
        choreDao.observeChores(householdId)

    suspend fun addChore(
        householdId: String,
        title: String,
        assignedMemberId: String?,
        dueAt: Long?,
    ): String {
        val id = UUID.randomUUID().toString()
        val chore = ChoreEntity(
            id = id,
            householdId = householdId,
            title = title,
            assignedMemberId = assignedMemberId,
            dueAt = dueAt,
        )
        choreDao.upsert(chore)
        if (remote.upsertChore(chore)) choreDao.markSynced(id)
        return id
    }

    suspend fun setCompleted(chore: ChoreEntity, completed: Boolean) {
        val now = System.currentTimeMillis()
        val completedAt = if (completed) now else null
        choreDao.setCompleted(chore.id, completed, completedAt, now)
        val updated = chore.copy(
            isCompleted = completed,
            completedAt = completedAt,
            updatedAt = now,
            isSynced = false,
        )
        if (remote.upsertChore(updated)) choreDao.markSynced(updated.id)
    }

    suspend fun deleteChore(chore: ChoreEntity) {
        val now = System.currentTimeMillis()
        choreDao.softDelete(chore.id, now)
        val updated = chore.copy(isDeleted = true, updatedAt = now, isSynced = false)
        if (remote.deleteChore(updated)) choreDao.markSynced(updated.id)
    }

    suspend fun syncPending() {
        choreDao.getUnsynced().forEach { chore ->
            val synced = if (chore.isDeleted) remote.deleteChore(chore) else remote.upsertChore(chore)
            if (synced) choreDao.markSynced(chore.id)
        }
    }
}
