package com.example.sortmate.data.remote

import android.util.Log
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.domain.model.SplitType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Thin Firestore adapter. Every call is guarded so the app can run entirely
 * offline (or before google-services.json is added). The Room database remains
 * the source of truth for the UI; Firestore is the multi-device transport.
 */
class FirestoreDataSource(private val enabled: Boolean) {

    private fun db(): FirebaseFirestore? {
        if (!enabled) return null
        return runCatching { FirebaseFirestore.getInstance() }
            .onFailure { Log.w(TAG, "Firestore unavailable", it) }
            .getOrNull()
    }

    suspend fun upsertHousehold(household: HouseholdEntity): Boolean = write {
        collection(HOUSEHOLDS).document(household.id)
            .set(household.toRemoteMap(), SetOptions.merge())
            .await()
    }

    suspend fun upsertMember(member: MemberEntity): Boolean = write {
        householdCollection(member.householdId, MEMBERS).document(member.id)
            .set(member.toRemoteMap(), SetOptions.merge())
            .await()
    }

    suspend fun upsertBill(bill: BillEntity): Boolean = write {
        householdCollection(bill.householdId, BILLS).document(bill.id)
            .set(bill.toRemoteMap(), SetOptions.merge())
            .await()
    }

    suspend fun upsertChore(chore: ChoreEntity): Boolean = write {
        householdCollection(chore.householdId, CHORES).document(chore.id)
            .set(chore.toRemoteMap(), SetOptions.merge())
            .await()
    }

    suspend fun deleteBill(bill: BillEntity): Boolean = write {
        householdCollection(bill.householdId, BILLS).document(bill.id).delete().await()
    }

    suspend fun deleteChore(chore: ChoreEntity): Boolean = write {
        householdCollection(chore.householdId, CHORES).document(chore.id).delete().await()
    }

    suspend fun findHouseholdByCode(code: String): HouseholdEntity? {
        val database = db() ?: return null
        return runCatching {
            database.collection(HOUSEHOLDS)
                .whereEqualTo("joinCode", code.uppercase())
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.toHousehold()
        }.onFailure { Log.w(TAG, "Could not look up join code", it) }.getOrNull()
    }

    suspend fun fetchHousehold(householdId: String): HouseholdEntity? {
        val database = db() ?: return null
        return runCatching {
            database.collection(HOUSEHOLDS).document(householdId).get().await().toHousehold()
        }.onFailure { Log.w(TAG, "Could not fetch household", it) }.getOrNull()
    }

    suspend fun fetchMembers(householdId: String): List<MemberEntity> = readList {
        householdCollection(householdId, MEMBERS).get().await().documents.mapNotNull { it.toMember(householdId) }
    }

    suspend fun fetchBills(householdId: String): List<BillEntity> = readList {
        householdCollection(householdId, BILLS).get().await().documents.mapNotNull { it.toBill(householdId) }
    }

    suspend fun fetchChores(householdId: String): List<ChoreEntity> = readList {
        householdCollection(householdId, CHORES).get().await().documents.mapNotNull { it.toChore(householdId) }
    }

    fun observeHousehold(householdId: String, onChange: (HouseholdEntity?) -> Unit): ListenerRegistration? {
        val database = db() ?: return null
        return database.collection(HOUSEHOLDS).document(householdId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Household listener failed", error)
                    return@addSnapshotListener
                }
                onChange(snapshot?.toHousehold())
            }
    }

    fun observeMembers(householdId: String, onChange: (List<MemberEntity>) -> Unit): ListenerRegistration? {
        val database = db() ?: return null
        return householdCollection(householdId, MEMBERS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Members listener failed", error)
                    return@addSnapshotListener
                }
                onChange(snapshot?.documents?.mapNotNull { it.toMember(householdId) }.orEmpty())
            }
    }

    fun observeBills(householdId: String, onChange: (List<BillEntity>) -> Unit): ListenerRegistration? {
        val database = db() ?: return null
        return householdCollection(householdId, BILLS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Bills listener failed", error)
                    return@addSnapshotListener
                }
                onChange(snapshot?.documents?.mapNotNull { it.toBill(householdId) }.orEmpty())
            }
    }

    fun observeChores(householdId: String, onChange: (List<ChoreEntity>) -> Unit): ListenerRegistration? {
        val database = db() ?: return null
        return householdCollection(householdId, CHORES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Chores listener failed", error)
                    return@addSnapshotListener
                }
                onChange(snapshot?.documents?.mapNotNull { it.toChore(householdId) }.orEmpty())
            }
    }

    private fun collection(path: String) = db()!!.collection(path)

    private fun householdCollection(householdId: String, child: String) =
        db()!!.collection(HOUSEHOLDS).document(householdId).collection(child)

    private suspend fun write(block: suspend () -> Unit): Boolean {
        if (!enabled || db() == null) return false
        return runCatching { block(); true }
            .onFailure { Log.w(TAG, "Firestore write failed", it) }
            .getOrDefault(false)
    }

    private suspend fun <T> readList(block: suspend () -> List<T>): List<T> {
        if (!enabled || db() == null) return emptyList()
        return runCatching { block() }
            .onFailure { Log.w(TAG, "Firestore read failed", it) }
            .getOrDefault(emptyList())
    }

    private fun HouseholdEntity.toRemoteMap() = mapOf(
        "name" to name,
        "joinCode" to joinCode,
        "quietStartMinutes" to quietStartMinutes,
        "quietEndMinutes" to quietEndMinutes,
        "geofenceRadiusMeters" to geofenceRadiusMeters,
        "homeLatitude" to homeLatitude,
        "homeLongitude" to homeLongitude,
        "updatedAt" to updatedAt,
    )

    private fun MemberEntity.toRemoteMap() = mapOf(
        "name" to name,
        "role" to role,
        "status" to status.name,
        "fcmToken" to fcmToken,
        "updatedAt" to updatedAt,
    )

    private fun BillEntity.toRemoteMap() = mapOf(
        "title" to title,
        "merchant" to merchant,
        "totalCents" to totalCents,
        "paidByMemberId" to paidByMemberId,
        "splitAmongMemberIds" to splitAmongMemberIds,
        "splitType" to splitType.name,
        "dueAt" to dueAt,
        "createdAt" to createdAt,
        "receiptUri" to receiptUri,
        "ocrConfidence" to ocrConfidence,
        "isSettled" to isSettled,
        "updatedAt" to updatedAt,
    )

    private fun ChoreEntity.toRemoteMap() = mapOf(
        "title" to title,
        "assignedMemberId" to assignedMemberId,
        "dueAt" to dueAt,
        "isCompleted" to isCompleted,
        "completedAt" to completedAt,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
    )

    private fun DocumentSnapshot.toHousehold(): HouseholdEntity? {
        val name = getString("name") ?: return null
        return HouseholdEntity(
            id = id,
            name = name,
            joinCode = getString("joinCode") ?: "",
            quietStartMinutes = getLong("quietStartMinutes")?.toInt() ?: (22 * 60),
            quietEndMinutes = getLong("quietEndMinutes")?.toInt() ?: (6 * 60),
            geofenceRadiusMeters = getDouble("geofenceRadiusMeters")?.toFloat() ?: 100f,
            homeLatitude = getDouble("homeLatitude"),
            homeLongitude = getDouble("homeLongitude"),
            updatedAt = getLong("updatedAt") ?: 0L,
            isSynced = true,
        )
    }

    private fun DocumentSnapshot.toMember(householdId: String): MemberEntity? {
        val name = getString("name") ?: return null
        return MemberEntity(
            id = id,
            householdId = householdId,
            name = name,
            role = getString("role") ?: "Roommate",
            status = PresenceStatus.from(getString("status")),
            fcmToken = getString("fcmToken"),
            updatedAt = getLong("updatedAt") ?: 0L,
            isSynced = true,
        )
    }

    private fun DocumentSnapshot.toBill(householdId: String): BillEntity? {
        val title = getString("title") ?: return null
        val total = getLong("totalCents") ?: return null
        return BillEntity(
            id = id,
            householdId = householdId,
            title = title,
            merchant = getString("merchant"),
            totalCents = total,
            paidByMemberId = getString("paidByMemberId") ?: "",
            splitAmongMemberIds = (get("splitAmongMemberIds") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty(),
            splitType = SplitType.entries.firstOrNull { it.name == getString("splitType") }
                ?: SplitType.EQUAL,
            dueAt = getLong("dueAt"),
            createdAt = getLong("createdAt") ?: 0L,
            receiptUri = getString("receiptUri"),
            ocrConfidence = getLong("ocrConfidence")?.toInt(),
            isSettled = getBoolean("isSettled") ?: false,
            updatedAt = getLong("updatedAt") ?: 0L,
            isSynced = true,
        )
    }

    private fun DocumentSnapshot.toChore(householdId: String): ChoreEntity? {
        val title = getString("title") ?: return null
        return ChoreEntity(
            id = id,
            householdId = householdId,
            title = title,
            assignedMemberId = getString("assignedMemberId"),
            dueAt = getLong("dueAt"),
            isCompleted = getBoolean("isCompleted") ?: false,
            completedAt = getLong("completedAt"),
            createdAt = getLong("createdAt") ?: 0L,
            updatedAt = getLong("updatedAt") ?: 0L,
            isSynced = true,
        )
    }

    private companion object {
        const val TAG = "FirestoreDataSource"
        const val HOUSEHOLDS = "households"
        const val MEMBERS = "members"
        const val BILLS = "bills"
        const val CHORES = "chores"
    }
}
