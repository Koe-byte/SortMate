package com.example.sortmate.data.repository

import androidx.room.withTransaction
import com.example.sortmate.data.local.SortMateDatabase
import com.example.sortmate.data.local.dao.BillDao
import com.example.sortmate.data.local.dao.ChoreDao
import com.example.sortmate.data.local.dao.HouseholdDao
import com.example.sortmate.data.local.dao.MemberDao
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.remote.FirestoreDataSource
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.domain.model.SplitType
import com.example.sortmate.util.daysFromNow
import com.example.sortmate.util.normalizedJoinCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.util.UUID

class HouseholdRepository(
    private val database: SortMateDatabase,
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val billDao: BillDao,
    private val choreDao: ChoreDao,
    private val preferences: UserPreferences,
    private val remote: FirestoreDataSource,
    private val auth: AuthRepository,
) {

    val session = preferences.session

    fun observeHousehold(householdId: String): Flow<HouseholdEntity?> =
        householdDao.observe(householdId)

    fun observeMembers(householdId: String): Flow<List<MemberEntity>> =
        memberDao.observeMembers(householdId)

    fun observeCurrentMember(): Flow<MemberEntity?> =
        preferences.session.flatMapLatest { current ->
            current.memberId?.let(memberDao::observeMember) ?: flowOf(null)
        }

    suspend fun getHousehold(householdId: String): HouseholdEntity? =
        householdDao.get(householdId)

    suspend fun getCurrentMember(): MemberEntity? {
        val current = preferences.session.first()
        return current.memberId?.let { memberDao.get(it) }
    }

    suspend fun createHousehold(
        householdName: String,
        displayName: String,
        quietStartMinutes: Int = 22 * 60,
        quietEndMinutes: Int = 6 * 60,
    ): Result<String> = runCatching {
        val memberId = auth.ensureUserId() ?: localMemberId()
        val householdId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val household = HouseholdEntity(
            id = householdId,
            name = householdName.trim().ifBlank { "Our household" },
            joinCode = generateJoinCode(),
            quietStartMinutes = quietStartMinutes,
            quietEndMinutes = quietEndMinutes,
            updatedAt = now,
        )
        val member = MemberEntity(
            id = memberId,
            householdId = householdId,
            name = displayName.trim().ifBlank { "Roommate" },
            role = "Leader",
            status = PresenceStatus.HOME,
            updatedAt = now,
        )

        database.withTransaction {
            householdDao.upsert(household)
            memberDao.upsert(member)
        }
        preferences.saveOnboarding(memberId, householdId, member.name)
        pushHousehold(household)
        pushMember(member)
        householdId
    }

    /**
     * Seeds a fully populated local household so the pipeline can be demonstrated
     * even without Firebase or a second phone.
     */
    suspend fun createDemoHousehold(displayName: String): Result<String> = runCatching {
        val memberId = auth.ensureUserId() ?: localMemberId()
        val householdId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val name = displayName.trim().ifBlank { "Nash" }

        val household = HouseholdEntity(
            id = householdId,
            name = "Alangilan Room 4B",
            joinCode = "SORTME",
            quietStartMinutes = 22 * 60,
            quietEndMinutes = 6 * 60,
            geofenceRadiusMeters = 120f,
            updatedAt = now,
        )
        val me = MemberEntity(
            id = memberId,
            householdId = householdId,
            name = name,
            role = "Leader / Backend",
            status = PresenceStatus.HOME,
            updatedAt = now,
        )
        val kotnie = MemberEntity(
            id = "$householdId-member-kotnie",
            householdId = householdId,
            name = "Kotnie Edrae",
            role = "UI / UX",
            status = PresenceStatus.CAMPUS,
            updatedAt = now,
        )
        val romelie = MemberEntity(
            id = "$householdId-member-romelie",
            householdId = householdId,
            name = "Romelie Joy",
            role = "Documentation",
            status = PresenceStatus.AWAY,
            updatedAt = now,
        )
        val participants = listOf(me.id, kotnie.id, romelie.id)

        val bills = listOf(
            BillEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Weekly groceries",
                merchant = "Puregold",
                totalCents = 124_850,
                paidByMemberId = me.id,
                splitAmongMemberIds = participants,
                splitType = SplitType.EQUAL,
                dueAt = daysFromNow(2),
                createdAt = now - 86_400_000L,
                ocrConfidence = 94,
            ),
            BillEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Fiber internet",
                merchant = "PLDT Home",
                totalCents = 159_900,
                paidByMemberId = kotnie.id,
                splitAmongMemberIds = participants,
                dueAt = daysFromNow(5),
                createdAt = now - 172_800_000L,
            ),
            BillEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Water bill",
                merchant = "Batangas Water",
                totalCents = 48_000,
                paidByMemberId = romelie.id,
                splitAmongMemberIds = participants,
                dueAt = daysFromNow(-3),
                createdAt = now - 604_800_000L,
                isSettled = true,
            ),
        )

        val chores = listOf(
            ChoreEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Take out the trash",
                assignedMemberId = kotnie.id,
                dueAt = daysFromNow(0),
                createdAt = now - 43_200_000L,
            ),
            ChoreEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Clean the kitchen",
                assignedMemberId = romelie.id,
                dueAt = daysFromNow(1),
                createdAt = now - 43_200_000L,
            ),
            ChoreEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Buy dish soap",
                assignedMemberId = me.id,
                createdAt = now - 21_600_000L,
            ),
        )

        database.withTransaction {
            householdDao.upsert(household)
            memberDao.upsertAll(listOf(me, kotnie, romelie))
            billDao.upsertAll(bills)
            choreDao.upsertAll(chores)
        }
        preferences.saveOnboarding(memberId, householdId, name)

        pushHousehold(household)
        listOf(me, kotnie, romelie).forEach { pushMember(it) }
        bills.forEach { bill -> if (remote.upsertBill(bill)) billDao.markSynced(bill.id) }
        chores.forEach { chore -> if (remote.upsertChore(chore)) choreDao.markSynced(chore.id) }
        householdId
    }

    suspend fun joinHousehold(joinCode: String, displayName: String): Result<String> = runCatching {
        val code = normalizedJoinCode(joinCode)
        require(code.length == 6) { "Enter the 6-character household code." }
        val household = remote.findHouseholdByCode(code)
            ?: error("No household found. Check the code and your Firebase connection.")
        val memberId = auth.ensureUserId()
            ?: error("Firebase sign-in is unavailable. Create a local household instead.")
        val now = System.currentTimeMillis()
        val member = MemberEntity(
            id = memberId,
            householdId = household.id,
            name = displayName.trim().ifBlank { "Roommate" },
            role = "Roommate",
            status = PresenceStatus.HOME,
            updatedAt = now,
        )
        val remoteMembers = remote.fetchMembers(household.id).filterNot { it.id == memberId }
        val remoteBills = remote.fetchBills(household.id)
        val remoteChores = remote.fetchChores(household.id)

        database.withTransaction {
            householdDao.upsert(household)
            memberDao.upsert(member)
            memberDao.upsertAll(remoteMembers)
            billDao.upsertAll(remoteBills)
            choreDao.upsertAll(remoteChores)
        }
        preferences.saveOnboarding(memberId, household.id, member.name)
        pushMember(member)
        household.id
    }

    suspend fun updatePresence(status: PresenceStatus) {
        val current = preferences.session.first()
        val memberId = current.memberId ?: return
        val member = memberDao.get(memberId) ?: return
        val now = System.currentTimeMillis()
        memberDao.updateStatus(memberId, status, now)
        pushMember(member.copy(status = status, updatedAt = now, isSynced = false))
    }

    suspend fun updateFcmToken(token: String) {
        val current = preferences.session.first()
        val memberId = current.memberId ?: return
        val member = memberDao.get(memberId) ?: return
        val now = System.currentTimeMillis()
        memberDao.updateFcmToken(memberId, token, now)
        pushMember(member.copy(fcmToken = token, updatedAt = now, isSynced = false))
    }

    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        val current = preferences.session.first()
        val householdId = current.householdId ?: return
        val now = System.currentTimeMillis()
        householdDao.updateQuietHours(householdId, startMinutes, endMinutes, now)
        householdDao.get(householdId)?.let { pushHousehold(it) }
    }

    suspend fun saveHomeGeofence(latitude: Double, longitude: Double, radiusMeters: Float) {
        val current = preferences.session.first()
        val householdId = current.householdId ?: return
        val now = System.currentTimeMillis()
        householdDao.updateHomeGeofence(householdId, latitude, longitude, radiusMeters, now)
        householdDao.get(householdId)?.let { pushHousehold(it) }
    }

    suspend fun updateHouseholdName(name: String) {
        val current = preferences.session.first()
        val householdId = current.householdId ?: return
        householdDao.updateName(householdId, name.trim().ifBlank { "Our household" }, System.currentTimeMillis())
        householdDao.get(householdId)?.let { pushHousehold(it) }
    }

    suspend fun syncPending() {
        householdDao.getUnsynced().forEach { pushHousehold(it) }
        memberDao.getUnsynced().forEach { pushMember(it) }
    }

    /** Pulls a fresh snapshot when the background sync worker runs. */
    suspend fun refreshFromRemote() {
        val householdId = preferences.session.first().householdId ?: return
        val household = remote.fetchHousehold(householdId) ?: return
        val members = remote.fetchMembers(householdId)
        val bills = remote.fetchBills(householdId)
        val chores = remote.fetchChores(householdId)
        database.withTransaction {
            householdDao.upsert(household)
            if (members.isNotEmpty()) memberDao.upsertAll(members)
            if (bills.isNotEmpty()) billDao.upsertAll(bills)
            if (chores.isNotEmpty()) choreDao.upsertAll(chores)
        }
    }

    suspend fun resetLocalData() {
        database.withTransaction {
            billDao.clear()
            choreDao.clear()
            memberDao.clear()
            householdDao.clear()
        }
        preferences.clear()
    }

    private suspend fun pushHousehold(household: HouseholdEntity) {
        if (remote.upsertHousehold(household)) householdDao.markSynced(household.id)
    }

    private suspend fun pushMember(member: MemberEntity) {
        if (remote.upsertMember(member)) memberDao.markSynced(member.id)
    }

    private fun localMemberId(): String = "local-${UUID.randomUUID()}"

    private fun generateJoinCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(6) { append(alphabet.random()) }
        }
    }
}
