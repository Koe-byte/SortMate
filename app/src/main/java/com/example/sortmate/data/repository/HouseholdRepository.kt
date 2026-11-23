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
import com.example.sortmate.data.remote.RemoteDataSource
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.domain.model.SplitType
import com.example.sortmate.util.daysFromNow
import com.example.sortmate.util.normalizedJoinCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class HouseholdRepository(
    private val database: SortMateDatabase,
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val billDao: BillDao,
    private val choreDao: ChoreDao,
    private val preferences: UserPreferences,
    private val remote: RemoteDataSource,
    private val auth: AuthRepository,
) {

    val session = preferences.session
    private val upload = Mutex()

    fun observeHousehold(householdId: String): Flow<HouseholdEntity?> =
        householdDao.observe(householdId)

    fun observeMembers(householdId: String): Flow<List<MemberEntity>> =
        memberDao.observeMembers(householdId)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
        val cloudUserId = auth.ensureUserId()
        if (remote.isAvailable) {
            check(cloudUserId != null) {
                "Cloud sign-in is unavailable. Check your connection and try again."
            }
        }

        val memberId = cloudUserId ?: localMemberId()
        val householdId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val name = displayName.trim().ifBlank { "Roommate" }
        val household = HouseholdEntity(
            id = householdId,
            name = householdName.trim().ifBlank { "Our household" },
            joinCode = generateJoinCode(),
            quietStartMinutes = quietStartMinutes,
            quietEndMinutes = quietEndMinutes,
            updatedAt = now,
            isSynced = true,
        )
        val member = MemberEntity(
            id = memberId,
            householdId = householdId,
            name = name,
            role = "Leader",
            status = PresenceStatus.HOME,
            updatedAt = now,
            isSynced = true,
        )

        if (remote.isAvailable) {
            check(remote.createHousehold(household, member)) {
                "Supabase could not create the household. Check your connection and try again."
            }
        }

        database.withTransaction {
            householdDao.upsert(household)
            memberDao.upsert(member)
        }
        preferences.saveOnboarding(memberId, householdId, name, cloudBacked = remote.isAvailable)
        householdId
    }

    /**
     * Seeds a fully populated local household so the pipeline can be demonstrated
     * without a second phone or a network connection. Demo data never touches
     * Supabase, so it cannot pollute a real household.
     */
    suspend fun createDemoHousehold(displayName: String): Result<String> = runCatching {
        val memberId = localMemberId()
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
            isSynced = true,
        )
        val me = MemberEntity(
            id = memberId,
            householdId = householdId,
            name = name,
            role = "Leader / Backend",
            status = PresenceStatus.HOME,
            updatedAt = now,
            isSynced = true,
        )
        val kotnie = MemberEntity(
            id = "$householdId-member-kotnie",
            householdId = householdId,
            name = "Kotnie Edrae",
            role = "UI / UX",
            status = PresenceStatus.CAMPUS,
            updatedAt = now,
            isSynced = true,
        )
        val romelie = MemberEntity(
            id = "$householdId-member-romelie",
            householdId = householdId,
            name = "Romelie Joy",
            role = "Documentation",
            status = PresenceStatus.AWAY,
            updatedAt = now,
            isSynced = true,
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
                isSynced = true,
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
                isSynced = true,
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
                isSynced = true,
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
                isSynced = true,
            ),
            ChoreEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Clean the kitchen",
                assignedMemberId = romelie.id,
                dueAt = daysFromNow(1),
                createdAt = now - 43_200_000L,
                isSynced = true,
            ),
            ChoreEntity(
                id = UUID.randomUUID().toString(),
                householdId = householdId,
                title = "Buy dish soap",
                assignedMemberId = me.id,
                createdAt = now - 21_600_000L,
                isSynced = true,
            ),
        )

        database.withTransaction {
            householdDao.upsert(household)
            memberDao.upsertAll(listOf(me, kotnie, romelie))
            billDao.upsertAll(bills)
            choreDao.upsertAll(chores)
        }
        preferences.saveOnboarding(memberId, householdId, name, cloudBacked = false)
        householdId
    }

    suspend fun joinHousehold(joinCode: String, displayName: String): Result<String> = runCatching {
        val code = normalizedJoinCode(joinCode)
        require(code.length == 6) { "Enter the 6-character household code." }
        check(remote.isAvailable) {
            "Cloud sync is not configured. Use the demo household or add Supabase credentials."
        }

        val memberId = auth.ensureUserId()
            ?: error("Cloud sign-in is unavailable. Check your connection and try again.")
        val snapshot = remote.joinHousehold(code, memberId, displayName)
            ?: error("No household found for that code. Check the code and your connection.")

        val name = displayName.trim().ifBlank { "Roommate" }
        val me = snapshot.members.firstOrNull { it.id == memberId }
            ?: MemberEntity(
                id = memberId,
                householdId = snapshot.household.id,
                name = name,
                role = "Roommate",
                status = PresenceStatus.HOME,
                updatedAt = System.currentTimeMillis(),
                isSynced = true,
            )

        database.withTransaction {
            householdDao.upsert(snapshot.household.copy(isSynced = true))
            memberDao.upsertAll(snapshot.members.filterNot { it.id == memberId } + me)
            if (snapshot.bills.isNotEmpty()) billDao.upsertAll(snapshot.bills)
            if (snapshot.chores.isNotEmpty()) choreDao.upsertAll(snapshot.chores)
        }
        preferences.saveOnboarding(memberId, snapshot.household.id, me.name, cloudBacked = true)
        snapshot.household.id
    }

    suspend fun updatePresence(status: PresenceStatus) {
        val current = preferences.session.first()
        val memberId = current.memberId ?: return
        val now = System.currentTimeMillis()
        memberDao.updateStatus(memberId, status, now)
        pushMember(memberId)
    }

    suspend fun updateFcmToken(token: String) {
        val current = preferences.session.first()
        if (!current.cloudBacked) return
        val memberId = current.memberId ?: return
        val member = memberDao.get(memberId) ?: return
        if (member.fcmToken == token) return
        val now = System.currentTimeMillis()
        memberDao.updateFcmToken(memberId, token, now)
        pushMember(memberId)
    }

    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        val current = preferences.session.first()
        val householdId = current.householdId ?: return
        val now = System.currentTimeMillis()
        householdDao.updateQuietHours(householdId, startMinutes, endMinutes, now)
        pushHousehold(householdId)
    }

    suspend fun saveHomeGeofence(latitude: Double, longitude: Double, radiusMeters: Float) {
        val current = preferences.session.first()
        val householdId = current.householdId ?: return
        val now = System.currentTimeMillis()
        householdDao.updateHomeGeofence(householdId, latitude, longitude, radiusMeters, now)
        pushHousehold(householdId)
    }

    suspend fun saveCampusGeofence(latitude: Double, longitude: Double, radiusMeters: Float) {
        val current = preferences.session.first()
        val householdId = current.householdId ?: return
        val now = System.currentTimeMillis()
        householdDao.updateCampusGeofence(householdId, latitude, longitude, radiusMeters, now)
        pushHousehold(householdId)
    }

    suspend fun updateHouseholdName(name: String) {
        val current = preferences.session.first()
        val householdId = current.householdId ?: return
        householdDao.updateName(
            householdId,
            name.trim().ifBlank { "Our household" },
            System.currentTimeMillis(),
        )
        pushHousehold(householdId)
    }

    suspend fun syncPending() {
        if (!remote.isAvailable) return
        val householdId = preferences.session.first().cloudHouseholdId ?: return
        pushHousehold(householdId)
        memberDao.getUnsynced(householdId).forEach { pushMember(it.id) }
    }

    /**
     * Pulls a fresh snapshot when the app reconnects or the worker runs.
     * A newer local row that is still waiting in the outbox wins, so a slow
     * upload is never overwritten by the previous server state.
     */
    suspend fun refreshFromRemote() {
        if (!remote.isAvailable) return
        val householdId = preferences.session.first().cloudHouseholdId ?: return
        val household = remote.fetchHousehold(householdId) ?: return
        val members = remote.fetchMembers(householdId)
        val bills = remote.fetchBills(householdId)
        val chores = remote.fetchChores(householdId)
        database.withTransaction {
            householdDao.mergeRemote(household)
            members.forEach { memberDao.mergeRemote(it) }
            bills.forEach { billDao.mergeRemote(it) }
            chores.forEach { choreDao.mergeRemote(it) }
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

    private suspend fun pushHousehold(id: String) = upload.withLock {
        val household = householdDao.get(id) ?: return@withLock
        val session = preferences.session.first()
        if (household.isSynced || session.householdId != id) return@withLock
        if (!session.cloudBacked || remote.upsertHousehold(household)) {
            householdDao.markSynced(household.id, household.updatedAt)
        }
    }

    private suspend fun pushMember(id: String) = upload.withLock {
        val member = memberDao.get(id) ?: return@withLock
        val session = preferences.session.first()
        if (member.isSynced || session.householdId != member.householdId || session.memberId != id) return@withLock
        if (!session.cloudBacked || remote.upsertMember(member)) {
            memberDao.markSynced(member.id, member.updatedAt)
        }
    }

    private fun localMemberId(): String = "local-${UUID.randomUUID()}"

    private fun generateJoinCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(6) { append(alphabet.random()) }
        }
    }
}
