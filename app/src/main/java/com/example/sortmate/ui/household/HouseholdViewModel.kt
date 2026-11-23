package com.example.sortmate.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.di.AppContainer
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.location.GeofenceManager
import com.example.sortmate.location.hasFineLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NotificationSetting {
    BILLS,
    CHORES,
    QUIET_HOURS,
    ACTIVITY,
}

enum class LocationTarget {
    HOME,
    CAMPUS,
}

data class HouseholdUiState(
    val loading: Boolean = true,
    val household: HouseholdEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val currentMember: MemberEntity? = null,
    val displayName: String = "",
    val cloudAvailable: Boolean = false,
    val isLocating: Boolean = false,
    val billReminders: Boolean = true,
    val choreReminders: Boolean = true,
    val quietHoursAlerts: Boolean = true,
    val activityAlerts: Boolean = true,
) {
    val isLeader: Boolean get() = currentMember?.role?.contains("Leader", ignoreCase = true) == true
}

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val preferences: UserPreferences = container.userPreferences
    private val repository = container.householdRepository

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _isLocating = MutableStateFlow(false)

    val uiState: StateFlow<HouseholdUiState> = preferences.session
        .flatMapLatest { session ->
            val householdId = session.householdId
            if (householdId == null) {
                flowOf(HouseholdUiState(loading = false))
            } else {
                combine(
                    repository.observeHousehold(householdId),
                    repository.observeMembers(householdId),
                    _isLocating,
                ) { household, members, locating ->
                    HouseholdUiState(
                        loading = false,
                        household = household,
                        members = members,
                        currentMember = members.firstOrNull { it.id == session.memberId },
                        displayName = session.displayName.orEmpty(),
                        cloudAvailable = session.cloudBacked && container.supabaseAvailable,
                        isLocating = locating,
                        billReminders = session.billReminders,
                        choreReminders = session.choreReminders,
                        quietHoursAlerts = session.quietHoursAlerts,
                        activityAlerts = session.activityAlerts,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HouseholdUiState(cloudAvailable = container.supabaseAvailable),
        )

    fun updatePresence(status: PresenceStatus) {
        viewModelScope.launch {
            repository.updatePresence(status)
            _message.value = "Status set to ${status.label}."
        }
    }

    fun simulateBoundary(status: PresenceStatus) {
        viewModelScope.launch {
            repository.updatePresence(status)
            val session = preferences.session.first()
            if (session.activityAlerts) container.notificationHelper.showPresenceUpdate(status)
            _message.value = "Simulated presence: ${status.label}."
        }
    }

    fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            repository.setQuietHours(startMinutes, endMinutes)
            val session = preferences.session.first()
            container.scheduleQuietHours(startMinutes, session.quietHoursAlerts)
            _message.value = "Quiet hours updated."
        }
    }

    fun setNotificationSetting(setting: NotificationSetting, enabled: Boolean) {
        viewModelScope.launch {
            when (setting) {
                NotificationSetting.BILLS -> {
                    preferences.setBillReminders(enabled)
                    container.scheduleBillReminders(enabled)
                }
                NotificationSetting.CHORES -> {
                    preferences.setChoreReminders(enabled)
                    container.scheduleChoreReminders(enabled)
                }
                NotificationSetting.QUIET_HOURS -> {
                    preferences.setQuietHoursAlerts(enabled)
                    val start = uiState.value.household?.quietStartMinutes ?: (22 * 60)
                    container.scheduleQuietHours(start, enabled)
                }
                NotificationSetting.ACTIVITY -> preferences.setActivityAlerts(enabled)
            }
        }
    }

    fun useCurrentLocation(target: LocationTarget) {
        if (_isLocating.value) return
        viewModelScope.launch {
            if (!hasFineLocation(container.appContext)) {
                _message.value = "Allow precise location first, then try again."
                return@launch
            }
            _isLocating.value = true
            try {
                val location = container.locationProvider.currentLocation()
                if (location == null) {
                    _message.value = "No location fix yet. Move near a window and retry."
                    return@launch
                }
                val household = uiState.value.household ?: return@launch
                val radius = when (target) {
                    LocationTarget.HOME -> household.geofenceRadiusMeters
                    LocationTarget.CAMPUS -> household.campusRadiusMeters
                }
                when (target) {
                    LocationTarget.HOME -> repository.saveHomeGeofence(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusMeters = radius,
                    )
                    LocationTarget.CAMPUS -> repository.saveCampusGeofence(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusMeters = radius,
                    )
                }
                val registration = when (target) {
                    LocationTarget.HOME -> container.geofenceManager.registerHomeGeofence(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusMeters = radius,
                    )
                    LocationTarget.CAMPUS -> container.geofenceManager.registerCampusGeofence(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        radiusMeters = radius,
                    )
                }
                val label = if (target == LocationTarget.HOME) "Home" else "Campus"
                registration.onSuccess {
                    _message.value = "$label boundary saved and geofence registered."
                }.onFailure { error ->
                    _message.value = error.message
                        ?: "Boundary saved, but the geofence could not be registered."
                }
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun registerSavedGeofence(target: LocationTarget) {
        val household = uiState.value.household ?: return
        viewModelScope.launch {
            val result = when (target) {
                LocationTarget.HOME -> container.geofenceManager.registerHomeGeofence(
                    household.homeLatitude ?: return@launch, household.homeLongitude ?: return@launch,
                    household.geofenceRadiusMeters,
                )
                LocationTarget.CAMPUS -> container.geofenceManager.registerCampusGeofence(
                    household.campusLatitude ?: return@launch, household.campusLongitude ?: return@launch,
                    household.campusRadiusMeters,
                )
            }
            _message.value = result.fold({ "Geofence refreshed." }, { it.message ?: "Could not register geofence." })
        }
    }

    fun removeGeofence(target: LocationTarget) {
        val id = when (target) {
            LocationTarget.HOME -> GeofenceManager.HOME_GEOFENCE_ID
            LocationTarget.CAMPUS -> GeofenceManager.CAMPUS_GEOFENCE_ID
        }
        viewModelScope.launch {
            _message.value = container.geofenceManager.remove(id).fold(
                { "Fence unregistered on this phone until refreshed or restarted after boot." },
                { it.message ?: "Could not remove the fence." },
            )
        }
    }

    fun updateHouseholdName(name: String) {
        viewModelScope.launch {
            repository.updateHouseholdName(name)
            _message.value = "Household name updated."
        }
    }

    fun resetLocalData() {
        viewModelScope.launch {
            container.geofenceManager.removeAllGeofences()
            repository.resetLocalData()
            _message.value = null
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
