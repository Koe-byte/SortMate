package com.example.sortmate.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.sortmate.domain.model.SplitType

@Entity(
    tableName = "bills",
    indices = [Index("householdId"), Index("dueAt")],
)
data class BillEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val title: String,
    val merchant: String? = null,
    val totalCents: Long,
    val paidByMemberId: String,
    val splitAmongMemberIds: List<String>,
    val splitType: SplitType = SplitType.EQUAL,
    val dueAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val receiptUri: String? = null,
    val ocrConfidence: Int? = null,
    val isSettled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
) {
    fun shareCents(memberId: String): Long {
        if (memberId !in splitAmongMemberIds || splitAmongMemberIds.isEmpty()) return 0L
        val base = totalCents / splitAmongMemberIds.size
        val remainder = (totalCents % splitAmongMemberIds.size).toInt()
        val index = splitAmongMemberIds.indexOf(memberId)
        return if (index < remainder) base + 1 else base
    }
}
