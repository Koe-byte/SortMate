package com.example.sortmate.ui.chores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.repository.ChoreRepository
import com.example.sortmate.data.repository.HouseholdRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChoresUiState(
    val loading: Boolean = true,
    val household: HouseholdEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val chores: List<ChoreEntity> = emptyList(),
    val currentMemberId: String? = null,
) {
    val openChores: List<ChoreEntity> get() = chores.filterNot { it.isCompleted }
    val doneChores: List<ChoreEntity> get() = chores.filter { it.isCompleted }
    fun memberName(memberId: String?): String =
        members.firstOrNull { it.id == memberId }?.name ?: "Anyone"
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChoresViewModel(
    private val householdRepository: HouseholdRepository,
    private val choreRepository: ChoreRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    val uiState: StateFlow<ChoresUiState> = preferences.session
        .flatMapLatest { session ->
            val householdId = session.householdId
            if (householdId == null) {
                flowOf(ChoresUiState(loading = false))
            } else {
                combine(
                    householdRepository.observeHousehold(householdId),
                    householdRepository.observeMembers(householdId),
                    choreRepository.observeChores(householdId),
                ) { household, members, chores ->
                    ChoresUiState(
                        loading = false,
                        household = household,
                        members = members,
                        chores = chores,
                        currentMemberId = session.memberId,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChoresUiState(),
        )

    fun addChore(title: String, assignedMemberId: String?, dueAt: Long?) {
        val householdId = uiState.value.household?.id ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            choreRepository.addChore(
                householdId = householdId,
                title = title.trim(),
                assignedMemberId = assignedMemberId,
                dueAt = dueAt,
            )
        }
    }

    fun toggle(chore: ChoreEntity) {
        viewModelScope.launch { choreRepository.setCompleted(chore, !chore.isCompleted) }
    }

    fun delete(chore: ChoreEntity) {
        viewModelScope.launch { choreRepository.deleteChore(chore) }
    }
}
