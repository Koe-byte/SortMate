package com.example.sortmate.data.repository

import com.example.sortmate.data.local.dao.ChoreDao
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.remote.RemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ChoreRepository(
    private val choreDao: ChoreDao,
    private val remote: RemoteDataSource,
    private val preferences: UserPreferences,
) {
    private val upload = Mutex()

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
        push(chore.id)
        return id
    }

    suspend fun setCompleted(chore: ChoreEntity, completed: Boolean) {
        val now = System.currentTimeMillis()
        val completedAt = if (completed) now else null
        choreDao.setCompleted(chore.id, completed, completedAt, now)
        push(chore.id)
    }

    suspend fun deleteChore(chore: ChoreEntity) {
        val now = System.currentTimeMillis()
        choreDao.softDelete(chore.id, now)
        push(chore.id)
    }

    suspend fun syncPending() {
        if (!remote.isAvailable) return
        val householdId = preferences.session.first().cloudHouseholdId ?: return
        choreDao.getUnsynced(householdId).forEach { push(it.id) }
    }

    private suspend fun push(id: String) = upload.withLock {
        val chore = choreDao.get(id) ?: return@withLock
        val session = preferences.session.first()
        if (chore.isSynced || session.householdId != chore.householdId) return@withLock
        if (!session.cloudBacked || remote.upsertChore(chore)) {
            choreDao.markSynced(id, chore.updatedAt)
        }
    }
}
