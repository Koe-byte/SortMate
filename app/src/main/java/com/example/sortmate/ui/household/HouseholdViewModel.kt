package com.example.sortmate.ui.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.di.AppContainer
import com.example.sortmate.domain.model.PresenceStatus
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

data class HouseholdUiState(
    val loading: Boolean = true,
    val household: HouseholdEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val currentMember: MemberEntity? = null,
    val displayName: String = "",
    val firebaseAvailable: Boolean = false,
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
                        firebaseAvailable = container.firebaseAvailable,
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
            initialValue = HouseholdUiState(firebaseAvailable = container.firebaseAvailable),
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
            _message.value = "Simulated geofence ${if (status == PresenceStatus.HOME) "arrival" else "exit"}."
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

    fun useCurrentLocation() {
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
                repository.saveHomeGeofence(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = household.geofenceRadiusMeters,
                )
                container.geofenceManager.registerHomeGeofence(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusMeters = household.geofenceRadiusMeters,
                ).onSuccess {
                    _message.value = "Home boundary saved and geofence registered."
                }.onFailure { error ->
                    _message.value = error.message
                        ?: "Boundary saved, but the geofence could not be registered."
                }
            } finally {
                _isLocating.value = false
            }
        }
    }

    fun registerSavedGeofence() {
        val household = uiState.value.household ?: return
        val latitude = household.homeLatitude ?: return
        val longitude = household.homeLongitude ?: return
        viewModelScope.launch {
            container.geofenceManager.registerHomeGeofence(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = household.geofenceRadiusMeters,
            ).onSuccess {
                _message.value = "Geofence refreshed."
            }.onFailure { error ->
                _message.value = error.message ?: "Could not register the geofence."
            }
        }
    }

    fun removeGeofence() {
        container.geofenceManager.removeHomeGeofence()
        _message.value = "Geofence removed. Background status updates are off."
    }

    fun updateHouseholdName(name: String) {
        viewModelScope.launch {
            repository.updateHouseholdName(name)
            _message.value = "Household name updated."
        }
    }

    fun resetLocalData() {
        viewModelScope.launch {
            repository.resetLocalData()
            _message.value = null
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
