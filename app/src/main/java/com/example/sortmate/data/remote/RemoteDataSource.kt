package com.example.sortmate.data.remote

import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity

/** Cancels a household's realtime listener. */
fun interface RemoteSubscription {
    fun cancel()
}

/** Everything a joining device needs to render a household offline. */
data class RemoteSnapshot(
    val household: HouseholdEntity,
    val members: List<MemberEntity>,
    val bills: List<BillEntity>,
    val chores: List<ChoreEntity>,
)

/**
 * Multi-device transport. Room remains the UI source of truth; this interface
 * is the only place that knows about the cloud vendor.
 */
interface RemoteDataSource {

    val isAvailable: Boolean

    /** Creates the household and the leader's member row atomically. */
    suspend fun createHousehold(household: HouseholdEntity, member: MemberEntity): Boolean

    /** Resolves a join code and returns the household plus its current data. */
    suspend fun joinHousehold(
        joinCode: String,
        memberId: String,
        displayName: String,
    ): RemoteSnapshot?

    suspend fun upsertHousehold(household: HouseholdEntity): Boolean
    suspend fun upsertMember(member: MemberEntity): Boolean
    suspend fun upsertBill(bill: BillEntity): Boolean
    suspend fun upsertChore(chore: ChoreEntity): Boolean

    suspend fun fetchHousehold(householdId: String): HouseholdEntity?
    suspend fun fetchMembers(householdId: String): List<MemberEntity>
    suspend fun fetchBills(householdId: String): List<BillEntity>
    suspend fun fetchChores(householdId: String): List<ChoreEntity>

    fun observeHousehold(
        householdId: String,
        onChange: suspend (HouseholdEntity?) -> Unit,
    ): RemoteSubscription

    fun observeMembers(
        householdId: String,
        onChange: suspend (List<MemberEntity>) -> Unit,
    ): RemoteSubscription

    fun observeBills(
        householdId: String,
        onChange: suspend (List<BillEntity>) -> Unit,
    ): RemoteSubscription

    fun observeChores(
        householdId: String,
        onChange: suspend (List<ChoreEntity>) -> Unit,
    ): RemoteSubscription
}
