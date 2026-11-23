package com.example.sortmate.data.sync

import com.example.sortmate.data.local.dao.BillDao
import com.example.sortmate.data.local.dao.ChoreDao
import com.example.sortmate.data.local.dao.HouseholdDao
import com.example.sortmate.data.local.dao.MemberDao
import com.example.sortmate.data.remote.RemoteDataSource
import com.example.sortmate.data.remote.RemoteSubscription

/**
 * Keeps the Room cache live while the app process is running by listening to
 * Supabase Realtime changes. Background/offline writes are handled by SyncWorker.
 *
 * Remote rows never overwrite a newer local row that is still waiting in the
 * outbox, otherwise a slow upload could clobber an edit made on this device.
 */
class RealtimeSyncManager(
    private val remote: RemoteDataSource,
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val billDao: BillDao,
    private val choreDao: ChoreDao,
) {

    private var subscriptions: List<RemoteSubscription> = emptyList()

    fun start(householdId: String) {
        stop()
        if (!remote.isAvailable) return

        subscriptions = listOf(
            remote.observeHousehold(householdId) { it?.let { row -> householdDao.mergeRemote(row) } },
            remote.observeMembers(householdId) { rows -> rows.forEach { memberDao.mergeRemote(it) } },
            remote.observeBills(householdId) { rows -> rows.forEach { billDao.mergeRemote(it) } },
            remote.observeChores(householdId) { rows -> rows.forEach { choreDao.mergeRemote(it) } },
        )
    }

    fun stop() {
        subscriptions.forEach { it.cancel() }
        subscriptions = emptyList()
    }
}
