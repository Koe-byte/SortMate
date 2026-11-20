package com.example.sortmate.di

import android.content.Context
import com.example.sortmate.data.local.SortMateDatabase
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.remote.FirestoreDataSource
import com.example.sortmate.data.repository.AuthRepository
import com.example.sortmate.data.repository.BillRepository
import com.example.sortmate.data.repository.ChoreRepository
import com.example.sortmate.data.repository.HouseholdRepository
import com.example.sortmate.data.sync.RealtimeSyncManager
import com.example.sortmate.location.GeofenceManager
import com.example.sortmate.location.LocationProvider
import com.example.sortmate.notifications.NotificationHelper
import com.example.sortmate.notifications.ReminderScheduler
import com.example.sortmate.ocr.ReceiptOcrProcessor
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppContainer(private val context: Context) {

    val appContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Cheap check that avoids initializing the Firebase SDK on the main thread.
     * Firebase auto-initializes via its ContentProvider when google-services.json
     * has been processed into resources. Without it the app runs in local mode.
     */
    val firebaseAvailable: Boolean by lazy {
        runCatching {
            appContext.resources.getIdentifier(
                "google_app_id",
                "string",
                appContext.packageName,
            ) != 0
        }.getOrDefault(false)
    }

    val database: SortMateDatabase by lazy { SortMateDatabase.build(appContext) }
    val userPreferences: UserPreferences by lazy { UserPreferences(appContext) }
    val remoteDataSource: FirestoreDataSource by lazy { FirestoreDataSource(firebaseAvailable) }
    val authRepository: AuthRepository by lazy { AuthRepository(firebaseAvailable) }

    val householdRepository: HouseholdRepository by lazy {
        HouseholdRepository(
            database = database,
            householdDao = database.householdDao(),
            memberDao = database.memberDao(),
            billDao = database.billDao(),
            choreDao = database.choreDao(),
            preferences = userPreferences,
            remote = remoteDataSource,
            auth = authRepository,
        )
    }

    val billRepository: BillRepository by lazy {
        BillRepository(database.billDao(), remoteDataSource)
    }

    val choreRepository: ChoreRepository by lazy {
        ChoreRepository(database.choreDao(), remoteDataSource)
    }

    val realtimeSyncManager: RealtimeSyncManager by lazy {
        RealtimeSyncManager(
            remote = remoteDataSource,
            householdDao = database.householdDao(),
            memberDao = database.memberDao(),
            billDao = database.billDao(),
            choreDao = database.choreDao(),
            scope = applicationScope,
        )
    }

    val geofenceManager: GeofenceManager by lazy { GeofenceManager(appContext) }
    val locationProvider: LocationProvider by lazy { LocationProvider(appContext) }
    val notificationHelper: NotificationHelper by lazy { NotificationHelper(appContext) }
    val receiptOcrProcessor: ReceiptOcrProcessor by lazy { ReceiptOcrProcessor() }

    fun scheduleBillReminders(enabled: Boolean) =
        ReminderScheduler.scheduleBills(appContext, enabled)

    fun scheduleChoreReminders(enabled: Boolean) =
        ReminderScheduler.scheduleChores(appContext, enabled)

    fun scheduleQuietHours(startMinutes: Int, enabled: Boolean) =
        ReminderScheduler.scheduleQuietHours(appContext, startMinutes, enabled)

    fun start() {
        notificationHelper.createChannels()
        ReminderScheduler.scheduleAll(appContext)
        applicationScope.launch {
            userPreferences.session
                .map { it.householdId }
                .distinctUntilChanged()
                .collectLatest { householdId ->
                    if (householdId == null) {
                        realtimeSyncManager.stop()
                        return@collectLatest
                    }
                    if (firebaseAvailable) realtimeSyncManager.start(householdId)
                    database.householdDao().observe(householdId)
                        .filterNotNull()
                        .distinctUntilChanged { a, b ->
                            a.quietStartMinutes == b.quietStartMinutes &&
                                a.quietEndMinutes == b.quietEndMinutes
                        }
                        .collect { household ->
                            val session = userPreferences.session.first()
                            ReminderScheduler.scheduleQuietHours(
                                appContext,
                                household.quietStartMinutes,
                                session.quietHoursAlerts,
                            )
                        }
                }
        }

        applicationScope.launch {
            if (!firebaseAvailable) return@launch
            userPreferences.session
                .map { it.memberId }
                .distinctUntilChanged()
                .collectLatest { memberId ->
                    if (memberId == null) return@collectLatest
                    val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                    if (token != null) householdRepository.updateFcmToken(token)
                }
        }
    }
}
