package com.example.sortmate.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sortmate.data.local.entity.BillEntity
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.data.repository.BillRepository
import com.example.sortmate.data.repository.HouseholdRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BillsUiState(
    val loading: Boolean = true,
    val household: HouseholdEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val currentMemberId: String? = null,
    val displayName: String = "",
) {
    val outstandingBills: List<BillEntity> get() = bills.filter { !it.isSettled && !it.isDeleted }
    val totalOutstandingCents: Long get() = outstandingBills.sumOf { it.totalCents }
    val myShareCents: Long
        get() = currentMemberId?.let { id -> outstandingBills.sumOf { it.shareCents(id) } } ?: 0L
    fun memberName(memberId: String): String =
        members.firstOrNull { it.id == memberId }?.name ?: "Unknown"
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillsViewModel(
    private val householdRepository: HouseholdRepository,
    private val billRepository: BillRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    val uiState: StateFlow<BillsUiState> = preferences.session
        .flatMapLatest { session ->
            val householdId = session.householdId
            if (householdId == null) {
                flowOf(BillsUiState(loading = false))
            } else {
                combine(
                    householdRepository.observeHousehold(householdId),
                    householdRepository.observeMembers(householdId),
                    billRepository.observeBills(householdId),
                ) { household, members, bills ->
                    BillsUiState(
                        loading = false,
                        household = household,
                        members = members,
                        bills = bills,
                        currentMemberId = session.memberId,
                        displayName = session.displayName.orEmpty(),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BillsUiState(),
        )

    fun setSettled(bill: BillEntity, settled: Boolean) {
        viewModelScope.launch { billRepository.setSettled(bill, settled) }
    }

    fun delete(bill: BillEntity) {
        viewModelScope.launch { billRepository.deleteBill(bill) }
    }
}
