package com.example.sortmate.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.local.entity.ChoreEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.repository.BillRepository
import com.example.sortmate.data.repository.ChoreRepository
import com.example.sortmate.data.repository.HouseholdRepository
import com.example.sortmate.domain.model.PresenceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val displayName: String = "",
    val household: HouseholdEntity? = null,
    val currentMember: MemberEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val chores: List<ChoreEntity> = emptyList(),
) {
    val openBills: List<BillEntity> get() = bills.filterNot { it.isSettled }
    val openChores: List<ChoreEntity> get() = chores.filterNot { it.isCompleted }
    val outstandingCents: Long get() = openBills.sumOf { it.totalCents }
    val myShareCents: Long
        get() = currentMember?.let { member ->
            openBills.sumOf { it.shareCents(member.id) }
        } ?: 0L
    val dueSoonChores: List<ChoreEntity>
        get() = openChores.sortedBy { it.dueAt ?: Long.MAX_VALUE }.take(3)
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val householdRepository: HouseholdRepository,
    private val billRepository: BillRepository,
    private val choreRepository: ChoreRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = preferences.session
        .flatMapLatest { session ->
            val householdId = session.householdId
            if (householdId == null) {
                flowOf(HomeUiState(loading = false))
            } else {
                combine(
                    householdRepository.observeHousehold(householdId),
                    householdRepository.observeMembers(householdId),
                    billRepository.observeBills(householdId),
                    choreRepository.observeChores(householdId),
                ) { household, members, bills, chores ->
                    HomeUiState(
                        loading = false,
                        displayName = session.displayName.orEmpty(),
                        household = household,
                        currentMember = members.firstOrNull { it.id == session.memberId },
                        members = members,
                        bills = bills,
                        chores = chores,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun setPresence(status: PresenceStatus) {
        viewModelScope.launch { householdRepository.updatePresence(status) }
    }

    fun toggleChore(chore: ChoreEntity) {
        viewModelScope.launch { choreRepository.setCompleted(chore, !chore.isCompleted) }
    }
}
