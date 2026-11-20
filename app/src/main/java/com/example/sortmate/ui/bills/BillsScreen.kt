package com.example.sortmate.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sortmate.ui.components.BillCard
import com.example.sortmate.ui.components.EmptyState
import com.example.sortmate.ui.components.InfoBanner
import com.example.sortmate.ui.components.ScreenHeader
import com.example.sortmate.ui.components.SectionTitle
import com.example.sortmate.util.formatPeso

@Composable
fun BillsScreen(viewModel: BillsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val settled = state.bills.filter { it.isSettled }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Bills",
                eyebrow = "Shared expenses",
                subtitle = state.household?.name ?: "Track receipts and split totals",
            )
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                SummaryCard(
                    outstandingCents = state.totalOutstandingCents,
                    myShareCents = state.myShareCents,
                    openCount = state.outstandingBills.size,
                )
            }
        }

        if (state.outstandingBills.isEmpty() && settled.isEmpty()) {
            item {
                EmptyState(
                    title = "No bills yet",
                    body = "Tap Scan bill, photograph a receipt, and SortMate fills in the total automatically.",
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                )
            }
        } else {
            if (state.outstandingBills.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionTitle(title = "Open · ${state.outstandingBills.size}")
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.outstandingBills.forEach { bill ->
                                BillCard(
                                    bill = bill,
                                    paidByName = state.memberName(bill.paidByMemberId),
                                    myShareCents = state.currentMemberId?.let(bill::shareCents),
                                    onSettleToggle = { viewModel.setSettled(bill, true) },
                                    onDelete = { viewModel.delete(bill) },
                                )
                            }
                        }
                    }
                }
            }

            if (settled.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(8.dp))
                        SectionTitle(title = "Settled · ${settled.size}")
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            settled.forEach { bill ->
                                BillCard(
                                    bill = bill,
                                    paidByName = state.memberName(bill.paidByMemberId),
                                    onSettleToggle = { viewModel.setSettled(bill, false) },
                                    onDelete = { viewModel.delete(bill) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                InfoBanner(
                    text = "Bills are split equally across every housemate. OCR confidence is saved with each scanned receipt.",
                    icon = Icons.Rounded.CloudOff,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    outstandingCents: Long,
    myShareCents: Long,
    openCount: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(26.dp))
            .padding(20.dp),
    ) {
        Text(
            text = "HOUSEHOLD OUTSTANDING",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatPeso(outstandingCents),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$openCount open",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Your share ${formatPeso(myShareCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        }
    }
}
