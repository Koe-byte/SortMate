package com.example.sortmate.data.repository

import com.example.sortmate.data.local.dao.BillDao
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.remote.RemoteDataSource
import com.example.sortmate.domain.model.SplitType
import com.example.sortmate.util.evaluationMark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class BillRepository(
    private val billDao: BillDao,
    private val remote: RemoteDataSource,
    private val preferences: UserPreferences,
) {
    private val upload = Mutex()

    fun observeBills(householdId: String): Flow<List<BillEntity>> =
        billDao.observeBills(householdId)

    suspend fun addBill(
        householdId: String,
        title: String,
        merchant: String?,
        totalCents: Long,
        paidByMemberId: String,
        splitAmongMemberIds: List<String>,
        dueAt: Long?,
        receiptUri: String? = null,
        ocrConfidence: Int? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val bill = BillEntity(
            id = id,
            householdId = householdId,
            title = title,
            merchant = merchant,
            totalCents = totalCents,
            paidByMemberId = paidByMemberId,
            splitAmongMemberIds = splitAmongMemberIds,
            splitType = SplitType.EQUAL,
            dueAt = dueAt,
            receiptUri = receiptUri,
            ocrConfidence = ocrConfidence,
        )
        billDao.upsert(bill)
        push(id)
        return id
    }

    suspend fun setSettled(bill: BillEntity, settled: Boolean) {
        val now = System.currentTimeMillis()
        billDao.setSettled(bill.id, settled, now)
        push(bill.id)
    }

    suspend fun deleteBill(bill: BillEntity) {
        val now = System.currentTimeMillis()
        billDao.softDelete(bill.id, now)
        push(bill.id)
    }

    suspend fun syncPending() {
        if (!remote.isAvailable) return
        val householdId = preferences.session.first().cloudHouseholdId ?: return
        billDao.getUnsynced(householdId).forEach { push(it.id) }
    }

    private suspend fun push(id: String) = upload.withLock {
        val bill = billDao.get(id) ?: return@withLock
        val session = preferences.session.first()
        if (bill.isSynced || session.householdId != bill.householdId) return@withLock
        if (!session.cloudBacked) {
            billDao.markSynced(id, bill.updatedAt)
            return@withLock
        }
        evaluationMark("bill_send id=$id")
        if (remote.upsertBill(bill) && billDao.markSynced(id, bill.updatedAt) == 1) {
            evaluationMark("bill_ack id=$id")
        }
    }
}
