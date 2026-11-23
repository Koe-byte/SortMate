package com.example.sortmate.di

import android.content.Context
import com.example.sortmate.BuildConfig
import com.example.sortmate.data.local.SortMateDatabase
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.remote.RemoteDataSource
import com.example.sortmate.data.remote.SupabaseDataSource
import com.example.sortmate.data.repository.AuthRepository
import com.example.sortmate.data.repository.BillRepository
import com.example.sortmate.data.repository.ChoreRepository
import com.example.sortmate.data.repository.HouseholdRepository
import com.example.sortmate.data.sync.NetworkMonitor
import com.example.sortmate.data.sync.RealtimeSyncManager
import com.example.sortmate.location.GeofenceManager
import com.example.sortmate.location.LocationProvider
import com.example.sortmate.notifications.NotificationHelper
import com.example.sortmate.notifications.ReminderScheduler
import com.example.sortmate.ocr.ReceiptOcrProcessor
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    /** True when local.properties supplied both Supabase values at build time. */
    private val supabaseConfigured: Boolean by lazy {
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
    }

    private val supabaseClient: SupabaseClient? by lazy {
        if (!supabaseConfigured) {
            null
        } else {
            runCatching {
                createSupabaseClient(
                    supabaseUrl = BuildConfig.SUPABASE_URL,
                    supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                ) {
                    install(Auth)
                    install(Postgrest)
                    install(Realtime)
                }
            }.getOrNull()
        }
    }

    val database: SortMateDatabase by lazy { SortMateDatabase.build(appContext) }
    val userPreferences: UserPreferences by lazy { UserPreferences(appContext) }
    val remoteDataSource: RemoteDataSource by lazy {
        SupabaseDataSource(supabaseClient, applicationScope)
    }
    val authRepository: AuthRepository by lazy { AuthRepository(supabaseClient) }
    val supabaseAvailable: Boolean get() = remoteDataSource.isAvailable

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
        BillRepository(database.billDao(), remoteDataSource, userPreferences)
    }

    val choreRepository: ChoreRepository by lazy {
        ChoreRepository(database.choreDao(), remoteDataSource, userPreferences)
    }

    val realtimeSyncManager: RealtimeSyncManager by lazy {
        RealtimeSyncManager(
            remote = remoteDataSource,
            householdDao = database.householdDao(),
            memberDao = database.memberDao(),
            billDao = database.billDao(),
            choreDao = database.choreDao(),
        )
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }
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
                    if (householdId == null) return@collectLatest
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
            combine(
                userPreferences.session.map { it.cloudHouseholdId }.distinctUntilChanged(),
                networkMonitor.isOnline,
            ) { householdId, online -> householdId to online }
                .collectLatest { (householdId, online) ->
                    if (householdId == null || !online || !supabaseAvailable) {
                        realtimeSyncManager.stop()
                        return@collectLatest
                    }
                    if (authRepository.ensureUserId() == null) {
                        realtimeSyncManager.stop()
                        return@collectLatest
                    }
                    realtimeSyncManager.start(householdId)
                    householdRepository.syncPending()
                    billRepository.syncPending()
                    choreRepository.syncPending()
                    householdRepository.refreshFromRemote()
                }
        }

        applicationScope.launch {
            if (!firebaseAvailable || !supabaseConfigured) return@launch
            userPreferences.session
                .map { session -> session.memberId.takeIf { session.cloudBacked } }
                .distinctUntilChanged()
                .collectLatest { memberId ->
                    if (memberId == null) return@collectLatest
                    // The sender targets legacy FCM registration tokens. register() opts into
                    // the installation-ID protocol and does not return a token for this backend.
                    @Suppress("DEPRECATION")
                    val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                    if (token != null) householdRepository.updateFcmToken(token)
                }
        }
    }
}
