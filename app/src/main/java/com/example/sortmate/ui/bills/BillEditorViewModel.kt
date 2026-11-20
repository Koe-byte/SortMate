package com.example.sortmate.ui.bills

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.data.prefs.UserPreferences
import com.example.sortmate.di.AppContainer
import com.example.sortmate.domain.ReceiptParser
import com.example.sortmate.domain.ReceiptScanResult
import com.example.sortmate.ocr.ReceiptImageStore
import com.example.sortmate.util.daysFromNow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class BillEditorUiState(
    val loading: Boolean = true,
    val household: HouseholdEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val currentMemberId: String? = null,
    val paidByMemberId: String = "",
    val title: String = "Shared bill",
    val merchant: String = "",
    val totalText: String = "",
    val dueAt: Long = daysFromNow(7),
    val ocrResult: ReceiptScanResult? = null,
    val receiptPath: String? = null,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val totalCents: Long? get() = ReceiptParser.parseAmountCents(totalText.replace("₱", "").trim())
    val splitCount: Int get() = members.size.coerceAtLeast(1)
    val perPersonCents: Long
        get() = totalCents?.let { it / splitCount } ?: 0L
}

private data class EditorContext(
    val household: HouseholdEntity?,
    val members: List<MemberEntity>,
    val memberId: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillEditorViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val preferences: UserPreferences = container.userPreferences
    private val repository = container.householdRepository

    private val _state = MutableStateFlow(BillEditorUiState())
    val state: StateFlow<BillEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.session
                .flatMapLatest { session ->
                    val householdId = session.householdId
                    if (householdId == null) {
                        flowOf(EditorContext(null, emptyList(), session.memberId))
                    } else {
                        combine(
                            repository.observeHousehold(householdId),
                            repository.observeMembers(householdId),
                        ) { household, members ->
                            EditorContext(household, members, session.memberId)
                        }
                    }
                }
                .collect { context ->
                    _state.update { current ->
                        val paidBy = current.paidByMemberId.ifBlank {
                            context.memberId ?: context.members.firstOrNull()?.id.orEmpty()
                        }
                        current.copy(
                            loading = false,
                            household = context.household,
                            members = context.members,
                            currentMemberId = context.memberId,
                            paidByMemberId = paidBy,
                        )
                    }
                }
        }
    }

    fun processImage(uri: Uri) {
        if (_state.value.isProcessing) return
        _state.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            val storedPath = ReceiptImageStore.copyToInternalStorage(container.appContext, uri)
            if (storedPath != null) {
                _state.update { it.copy(receiptPath = storedPath) }
            }
            runCatching {
                container.receiptOcrProcessor.recognize(container.appContext, uri)
            }.onSuccess { result ->
                val total = result.totalCents?.let {
                    String.format(Locale.US, "%.2f", it / 100.0)
                }.orEmpty()
                _state.update { current ->
                    current.copy(
                        isProcessing = false,
                        ocrResult = result,
                        totalText = total.ifBlank { current.totalText },
                        merchant = result.merchant?.takeIf { it.isNotBlank() } ?: current.merchant,
                        title = if (current.title.isBlank() || current.title == "Shared bill") {
                            result.merchant?.let { "Receipt · $it" } ?: "Receipt"
                        } else {
                            current.title
                        },
                        error = if (result.totalCents == null) {
                            "Total not detected. Type it in below, or retake the photo."
                        } else {
                            null
                        },
                    )
                }
            }.onFailure {
                _state.update { current ->
                    current.copy(
                        isProcessing = false,
                        error = "Could not read the receipt. Try again or enter it manually.",
                    )
                }
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value, error = null) }

    fun setMerchant(value: String) = _state.update { it.copy(merchant = value) }

    fun setTotal(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' || it == ',' }
        _state.update { it.copy(totalText = filtered, error = null) }
    }

    fun setPaidBy(memberId: String) = _state.update { it.copy(paidByMemberId = memberId) }

    fun setDueInDays(days: Long) = _state.update { it.copy(dueAt = daysFromNow(days)) }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.saved) return
        val household = current.household
        val total = current.totalCents
        if (household == null) {
            _state.update { it.copy(error = "Household not ready yet.") }
            return
        }
        if (total == null || total <= 0) {
            _state.update { it.copy(error = "Enter a valid total amount.") }
            return
        }
        if (current.paidByMemberId.isBlank()) {
            _state.update { it.copy(error = "Choose who paid.") }
            return
        }
        val participants = current.members.map { it.id }.ifEmpty { listOf(current.paidByMemberId) }
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                container.billRepository.addBill(
                    householdId = household.id,
                    title = current.title.trim().ifBlank { "Shared bill" },
                    merchant = current.merchant.trim().ifBlank { null },
                    totalCents = total,
                    paidByMemberId = current.paidByMemberId,
                    splitAmongMemberIds = participants,
                    dueAt = current.dueAt,
                    receiptUri = current.receiptPath,
                    ocrConfidence = current.ocrResult?.confidence,
                )
            }.onSuccess {
                _state.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = error.message ?: "Could not save the bill.",
                    )
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
