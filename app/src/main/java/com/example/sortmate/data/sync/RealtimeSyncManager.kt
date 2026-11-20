package com.example.sortmate.data.sync

import com.example.sortmate.data.local.dao.BillDao
import com.example.sortmate.data.local.dao.ChoreDao
import com.example.sortmate.data.local.dao.HouseholdDao
import com.example.sortmate.data.local.dao.MemberDao
import com.example.sortmate.data.remote.FirestoreDataSource
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps the Room cache live while the app process is running by listening to
 * Firestore snapshots. Background/offline writes are handled by SyncWorker.
 */
class RealtimeSyncManager(
    private val remote: FirestoreDataSource,
    private val householdDao: HouseholdDao,
    private val memberDao: MemberDao,
    private val billDao: BillDao,
    private val choreDao: ChoreDao,
    private val scope: CoroutineScope,
) {

    private var registrations: List<ListenerRegistration> = emptyList()

    fun start(householdId: String) {
        stop()
        registrations = listOfNotNull(
            remote.observeHousehold(householdId) { household ->
                household?.let { scope.launch { householdDao.upsert(it) } }
            },
            remote.observeMembers(householdId) { members ->
                scope.launch { memberDao.upsertAll(members) }
            },
            remote.observeBills(householdId) { bills ->
                scope.launch { billDao.upsertAll(bills) }
            },
            remote.observeChores(householdId) { chores ->
                scope.launch { choreDao.upsertAll(chores) }
            },
        )
    }

    fun stop() {
        registrations.forEach { it.remove() }
        registrations = emptyList()
    }
}
