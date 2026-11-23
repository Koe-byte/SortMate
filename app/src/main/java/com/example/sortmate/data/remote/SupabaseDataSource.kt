package com.example.sortmate.data.remote

import android.util.Log
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.domain.model.SplitType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Supabase Postgrest + Realtime adapter.
 *
 * Reads use snake_case DTOs, writes never send timestamps or audit columns:
 * Postgres triggers own `updated_at` and `created_by` / `updated_by`.
 */
class SupabaseDataSource(
    private val client: SupabaseClient?,
    private val scope: CoroutineScope,
) : RemoteDataSource {

    override val isAvailable: Boolean get() = client != null

    private val channelCounter = AtomicInteger(0)

    override suspend fun createHousehold(
        household: HouseholdEntity,
        member: MemberEntity,
    ): Boolean = withClient("create_household") { supabase ->
        supabase.postgrest.rpc(
            "create_household",
            buildJsonObject {
                put("p_id", household.id)
                put("p_name", household.name)
                put("p_join_code", household.joinCode)
                put("p_display_name", member.name)
                put("p_quiet_start", household.quietStartMinutes)
                put("p_quiet_end", household.quietEndMinutes)
            },
        )
        true
    } ?: false

    override suspend fun joinHousehold(
        joinCode: String,
        memberId: String,
        displayName: String,
    ): RemoteSnapshot? = withClient("join_household") { supabase ->
        val payload = supabase.postgrest.rpc(
            "join_household",
            buildJsonObject {
                put("p_join_code", joinCode)
                put("p_display_name", displayName)
            },
        ).decodeAs<JoinPayload>() // The scalar jsonb RPC returns an object, not a row array.

        val household = payload.household.toEntity()
        check(payload.member.id == memberId)
        RemoteSnapshot(
            household = household,
            members = supabase.rows<MemberRow>(TABLE_MEMBERS, household.id).map { it.toEntity() },
            bills = supabase.rows<BillRow>(TABLE_BILLS, household.id).map { it.toEntity() },
            chores = supabase.rows<ChoreRow>(TABLE_CHORES, household.id).map { it.toEntity() },
        )
    }

    override suspend fun upsertHousehold(household: HouseholdEntity): Boolean =
        withClient("update household") { supabase ->
            supabase.from(TABLE_HOUSEHOLDS).update(household.toWrite()) {
                filter { eq("id", household.id) }
                select(Columns.list("id"))
            }.decodeSingleOrNull<RowId>()?.id == household.id
        } ?: false

    override suspend fun upsertMember(member: MemberEntity): Boolean =
        withClient("update member") { supabase ->
            supabase.from(TABLE_MEMBERS).update(member.toWrite()) {
                filter { eq("id", member.id) }
                select(Columns.list("id"))
            }.decodeSingleOrNull<RowId>()?.id == member.id
        } ?: false

    override suspend fun upsertBill(bill: BillEntity): Boolean =
        withClient("upsert bill") { supabase ->
            supabase.from(TABLE_BILLS).upsert(bill.toWrite())
            true
        } ?: false

    override suspend fun upsertChore(chore: ChoreEntity): Boolean =
        withClient("upsert chore") { supabase ->
            supabase.from(TABLE_CHORES).upsert(chore.toWrite())
            true
        } ?: false

    private suspend inline fun <reified T : Any> SupabaseClient.rows(table: String, householdId: String): List<T> =
        from(table).select { filter { eq("household_id", householdId) } }.decodeList<T>()

    override suspend fun fetchHousehold(householdId: String): HouseholdEntity? =
        withClient("fetch household") { supabase ->
            supabase.from(TABLE_HOUSEHOLDS).select {
                filter { eq("id", householdId) }
                limit(1)
            }.decodeSingleOrNull<HouseholdRow>()?.toEntity()
        }

    override suspend fun fetchMembers(householdId: String): List<MemberEntity> =
        withClient("fetch members") { supabase ->
            supabase.rows<MemberRow>(TABLE_MEMBERS, householdId).map { it.toEntity() }
        } ?: emptyList()

    override suspend fun fetchBills(householdId: String): List<BillEntity> =
        withClient("fetch bills") { supabase ->
            supabase.rows<BillRow>(TABLE_BILLS, householdId).map { it.toEntity() }
        } ?: emptyList()

    override suspend fun fetchChores(householdId: String): List<ChoreEntity> =
        withClient("fetch chores") { supabase ->
            supabase.rows<ChoreRow>(TABLE_CHORES, householdId).map { it.toEntity() }
        } ?: emptyList()

    override fun observeHousehold(
        householdId: String,
        onChange: suspend (HouseholdEntity?) -> Unit,
    ): RemoteSubscription = observe(
        table = TABLE_HOUSEHOLDS,
        filterColumn = "id",
        householdId = householdId,
        fetch = { fetchHousehold(householdId) },
        onChange = onChange,
    )

    override fun observeMembers(
        householdId: String,
        onChange: suspend (List<MemberEntity>) -> Unit,
    ): RemoteSubscription = observe(
        table = TABLE_MEMBERS,
        householdId = householdId,
        fetch = { fetchMembers(householdId) },
        onChange = onChange,
    )

    override fun observeBills(
        householdId: String,
        onChange: suspend (List<BillEntity>) -> Unit,
    ): RemoteSubscription = observe(
        table = TABLE_BILLS,
        householdId = householdId,
        fetch = { fetchBills(householdId) },
        onChange = onChange,
    )

    override fun observeChores(
        householdId: String,
        onChange: suspend (List<ChoreEntity>) -> Unit,
    ): RemoteSubscription = observe(
        table = TABLE_CHORES,
        householdId = householdId,
        fetch = { fetchChores(householdId) },
        onChange = onChange,
    )

    private fun <T> observe(
        table: String,
        householdId: String,
        filterColumn: String = "household_id",
        fetch: suspend () -> T,
        onChange: suspend (T) -> Unit,
    ): RemoteSubscription {
        val supabase = client ?: return RemoteSubscription {}
        val channel = supabase.channel(
            "sortmate-$table-$householdId-${channelCounter.incrementAndGet()}",
        )
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            this.table = table
            filter(filterColumn, FilterOperator.EQ, householdId)
        }

        val job = scope.launch {
            val subscribed = channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }
            // Refetch after each subscription (including websocket reconnects), and on change.
            merge(subscribed.map { Unit }, changes.map { Unit }).conflate().collect {
                onChange(fetch())
            }
        }
        val subscription = scope.launch {
            runCatching { channel.subscribe() }
                .onFailure { Log.w(TAG, "Could not subscribe to $table", it) }
        }

        return RemoteSubscription {
            job.cancel()
            subscription.cancel()
            scope.launch { runCatching { supabase.realtime.removeChannel(channel) } }
        }
    }

    private suspend fun <T> withClient(
        operation: String,
        block: suspend (SupabaseClient) -> T,
    ): T? {
        val supabase = client ?: return null
        return try {
            block(supabase)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "$operation failed (${error.javaClass.simpleName})")
            null
        }
    }

    // -----------------------------------------------------------------------
    // DTOs
    // -----------------------------------------------------------------------

    @Serializable
    private data class RowId(val id: String)

    @Serializable
    private data class HouseholdRow(
        val id: String,
        val name: String,
        @SerialName("join_code") val joinCode: String,
        @SerialName("quiet_start_minutes") val quietStartMinutes: Int = 1320,
        @SerialName("quiet_end_minutes") val quietEndMinutes: Int = 360,
        @SerialName("geofence_radius_meters") val geofenceRadiusMeters: Float = 100f,
        @SerialName("home_latitude") val homeLatitude: Double? = null,
        @SerialName("home_longitude") val homeLongitude: Double? = null,
        @SerialName("campus_latitude") val campusLatitude: Double? = null,
        @SerialName("campus_longitude") val campusLongitude: Double? = null,
        @SerialName("campus_radius_meters") val campusRadiusMeters: Float = 100f,
        @SerialName("updated_at") val updatedAt: String? = null,
    ) {
        fun toEntity() = HouseholdEntity(
            id = id,
            name = name,
            joinCode = joinCode,
            quietStartMinutes = quietStartMinutes,
            quietEndMinutes = quietEndMinutes,
            geofenceRadiusMeters = geofenceRadiusMeters,
            homeLatitude = homeLatitude,
            homeLongitude = homeLongitude,
            campusLatitude = campusLatitude,
            campusLongitude = campusLongitude,
            campusRadiusMeters = campusRadiusMeters,
            updatedAt = updatedAt.toEpochMillis() ?: System.currentTimeMillis(),
            isSynced = true,
        )
    }

    @Serializable
    private data class MemberRow(
        val id: String,
        @SerialName("household_id") val householdId: String,
        @SerialName("auth_user_id") val authUserId: String,
        val name: String,
        val role: String? = null,
        val status: String = "AWAY",
        @SerialName("fcm_token") val fcmToken: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
    ) {
        fun toEntity() = MemberEntity(
            id = id,
            householdId = householdId,
            name = name,
            role = role ?: "Roommate",
            status = PresenceStatus.from(status),
            fcmToken = fcmToken,
            updatedAt = updatedAt.toEpochMillis() ?: System.currentTimeMillis(),
            isSynced = true,
        )
    }

    @Serializable
    private data class BillRow(
        val id: String,
        @SerialName("household_id") val householdId: String,
        val title: String,
        val merchant: String? = null,
        @SerialName("total_cents") val totalCents: Long,
        @SerialName("paid_by_member_id") val paidByMemberId: String,
        @SerialName("split_among_member_ids") val splitAmongMemberIds: List<String> = emptyList(),
        @SerialName("split_type") val splitType: String = "EQUAL",
        @SerialName("due_at") val dueAt: String? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("receipt_uri") val receiptUri: String? = null,
        @SerialName("ocr_confidence") val ocrConfidence: Int? = null,
        @SerialName("is_settled") val isSettled: Boolean = false,
        @SerialName("is_deleted") val isDeleted: Boolean = false,
        @SerialName("updated_at") val updatedAt: String? = null,
    ) {
        fun toEntity() = BillEntity(
            id = id,
            householdId = householdId,
            title = title,
            merchant = merchant,
            totalCents = totalCents,
            paidByMemberId = paidByMemberId,
            splitAmongMemberIds = splitAmongMemberIds,
            splitType = SplitType.entries.firstOrNull { it.name == splitType } ?: SplitType.EQUAL,
            dueAt = dueAt.toEpochMillis(),
            createdAt = createdAt.toEpochMillis() ?: 0L,
            receiptUri = receiptUri,
            ocrConfidence = ocrConfidence,
            isSettled = isSettled,
            isDeleted = isDeleted,
            updatedAt = updatedAt.toEpochMillis() ?: 0L,
            isSynced = true,
        )
    }

    @Serializable
    private data class ChoreRow(
        val id: String,
        @SerialName("household_id") val householdId: String,
        val title: String,
        @SerialName("assigned_member_id") val assignedMemberId: String? = null,
        @SerialName("due_at") val dueAt: String? = null,
        @SerialName("is_completed") val isCompleted: Boolean = false,
        @SerialName("completed_at") val completedAt: String? = null,
        @SerialName("is_deleted") val isDeleted: Boolean = false,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
    ) {
        fun toEntity() = ChoreEntity(
            id = id,
            householdId = householdId,
            title = title,
            assignedMemberId = assignedMemberId,
            dueAt = dueAt.toEpochMillis(),
            isCompleted = isCompleted,
            completedAt = completedAt.toEpochMillis(),
            createdAt = createdAt.toEpochMillis() ?: 0L,
            updatedAt = updatedAt.toEpochMillis() ?: 0L,
            isSynced = true,
        )
    }

    @Serializable
    private data class HouseholdWrite(
        val name: String,
        @SerialName("quiet_start_minutes") val quietStartMinutes: Int,
        @SerialName("quiet_end_minutes") val quietEndMinutes: Int,
        @SerialName("geofence_radius_meters") val geofenceRadiusMeters: Float,
        @SerialName("home_latitude") val homeLatitude: Double?,
        @SerialName("home_longitude") val homeLongitude: Double?,
        @SerialName("campus_latitude") val campusLatitude: Double?,
        @SerialName("campus_longitude") val campusLongitude: Double?,
        @SerialName("campus_radius_meters") val campusRadiusMeters: Float,
    )

    @Serializable
    private data class MemberWrite(
        val name: String,
        val status: String,
        @SerialName("fcm_token") val fcmToken: String?,
    )

    @Serializable
    private data class BillWrite(
        val id: String,
        @SerialName("household_id") val householdId: String,
        val title: String,
        val merchant: String?,
        @SerialName("total_cents") val totalCents: Long,
        @SerialName("paid_by_member_id") val paidByMemberId: String,
        @SerialName("split_among_member_ids") val splitAmongMemberIds: List<String>,
        @SerialName("split_type") val splitType: String,
        @SerialName("due_at") val dueAt: String?,
        @SerialName("receipt_uri") val receiptUri: String?,
        @SerialName("ocr_confidence") val ocrConfidence: Int?,
        @SerialName("is_settled") val isSettled: Boolean,
        @SerialName("is_deleted") val isDeleted: Boolean,
    )

    @Serializable
    private data class ChoreWrite(
        val id: String,
        @SerialName("household_id") val householdId: String,
        val title: String,
        @SerialName("assigned_member_id") val assignedMemberId: String?,
        @SerialName("due_at") val dueAt: String?,
        @SerialName("is_completed") val isCompleted: Boolean,
        @SerialName("completed_at") val completedAt: String?,
        @SerialName("is_deleted") val isDeleted: Boolean,
    )

    @Serializable
    private data class JoinPayload(
        val household: HouseholdRow,
        val member: MemberRow,
    )

    private fun HouseholdEntity.toWrite() = HouseholdWrite(
        name = name,
        quietStartMinutes = quietStartMinutes,
        quietEndMinutes = quietEndMinutes,
        geofenceRadiusMeters = geofenceRadiusMeters,
        homeLatitude = homeLatitude,
        homeLongitude = homeLongitude,
        campusLatitude = campusLatitude,
        campusLongitude = campusLongitude,
        campusRadiusMeters = campusRadiusMeters,
    )

    private fun MemberEntity.toWrite() = MemberWrite(
        name = name,
        status = status.name,
        fcmToken = fcmToken,
    )

    private fun BillEntity.toWrite() = BillWrite(
        id = id,
        householdId = householdId,
        title = title,
        merchant = merchant,
        totalCents = totalCents,
        paidByMemberId = paidByMemberId,
        splitAmongMemberIds = splitAmongMemberIds,
        splitType = splitType.name,
        dueAt = dueAt.toIsoString(),
        receiptUri = receiptUri,
        ocrConfidence = ocrConfidence,
        isSettled = isSettled,
        isDeleted = isDeleted,
    )

    private fun ChoreEntity.toWrite() = ChoreWrite(
        id = id,
        householdId = householdId,
        title = title,
        assignedMemberId = assignedMemberId,
        dueAt = dueAt.toIsoString(),
        isCompleted = isCompleted,
        completedAt = completedAt.toIsoString(),
        isDeleted = isDeleted,
    )

    private companion object {
        const val TAG = "SupabaseDataSource"
        const val TABLE_HOUSEHOLDS = "households"
        const val TABLE_MEMBERS = "members"
        const val TABLE_BILLS = "bills"
        const val TABLE_CHORES = "chores"

        fun Long?.toIsoString(): String? = this?.let { Instant.ofEpochMilli(it).toString() }

        fun String?.toEpochMillis(): Long? = this?.let { value ->
            runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
                .recoverCatching { Instant.parse(value).toEpochMilli() }
                .getOrNull()
        }
    }
}
