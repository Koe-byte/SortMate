package com.example.sortmate.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.repository.HouseholdRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OnboardingMode {
    CREATE,
    JOIN,
}

data class OnboardingUiState(
    val mode: OnboardingMode = OnboardingMode.CREATE,
    val displayName: String = "",
    val householdName: String = "",
    val joinCode: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val householdRepository: HouseholdRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setMode(mode: OnboardingMode) {
        _state.update { it.copy(mode = mode, error = null) }
    }

    fun setDisplayName(value: String) {
        _state.update { it.copy(displayName = value, error = null) }
    }

    fun setHouseholdName(value: String) {
        _state.update { it.copy(householdName = value, error = null) }
    }

    fun setJoinCode(value: String) {
        _state.update { it.copy(joinCode = value.uppercase().take(6), error = null) }
    }

    fun createHousehold() {
        val current = _state.value
        if (current.loading) return
        if (current.displayName.isBlank() || current.householdName.isBlank()) {
            _state.update { it.copy(error = "Add your name and a household name.") }
            return
        }
        launchOperation {
            householdRepository.createHousehold(
                householdName = current.householdName,
                displayName = current.displayName,
            )
        }
    }

    fun joinHousehold() {
        val current = _state.value
        if (current.loading) return
        if (current.displayName.isBlank()) {
            _state.update { it.copy(error = "Add the name your roommates will see.") }
            return
        }
        if (current.joinCode.length != 6) {
            _state.update { it.copy(error = "Enter the 6-character household code.") }
            return
        }
        launchOperation {
            householdRepository.joinHousehold(
                joinCode = current.joinCode,
                displayName = current.displayName,
            )
        }
    }

    fun loadDemo() {
        if (_state.value.loading) return
        launchOperation {
            householdRepository.createDemoHousehold(_state.value.displayName)
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun launchOperation(operation: suspend () -> Result<String>) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            // Opening the Room database on a cold start must stay off the main thread.
            withContext(Dispatchers.IO) { operation() }
                .onSuccess {
                    // The DataStore session flips the root navigator. Clearing the
                    // form also keeps the reused ViewModel fresh after a data reset.
                    _state.value = OnboardingUiState()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = error.message ?: "Something went wrong. Try again.",
                        )
                    }
                }
        }
    }
}
