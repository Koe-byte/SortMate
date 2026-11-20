package com.example.sortmate.data.repository

import com.example.sortmate.data.local.dao.BillDao
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.remote.FirestoreDataSource
import com.example.sortmate.domain.model.SplitType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BillRepository(
    private val billDao: BillDao,
    private val remote: FirestoreDataSource,
) {

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
        if (remote.upsertBill(bill)) billDao.markSynced(id)
        return id
    }

    suspend fun setSettled(bill: BillEntity, settled: Boolean) {
        val now = System.currentTimeMillis()
        billDao.setSettled(bill.id, settled, now)
        val updated = bill.copy(isSettled = settled, updatedAt = now, isSynced = false)
        if (remote.upsertBill(updated)) billDao.markSynced(updated.id)
    }

    suspend fun deleteBill(bill: BillEntity) {
        val now = System.currentTimeMillis()
        billDao.softDelete(bill.id, now)
        val updated = bill.copy(isDeleted = true, updatedAt = now, isSynced = false)
        if (remote.deleteBill(updated)) billDao.markSynced(updated.id)
    }

    suspend fun syncPending() {
        billDao.getUnsynced().forEach { bill ->
            val synced = if (bill.isDeleted) remote.deleteBill(bill) else remote.upsertBill(bill)
            if (synced) billDao.markSynced(bill.id)
        }
    }
}
