package com.example.sortmate.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sortmate.domain.model.PresenceStatus
import com.example.sortmate.ui.components.Avatar
import com.example.sortmate.ui.components.BillCard
import com.example.sortmate.ui.components.ChoreCard
import com.example.sortmate.ui.components.EmptyState
import com.example.sortmate.ui.components.PresencePill
import com.example.sortmate.ui.components.markFirstDraw
import com.example.sortmate.ui.components.SectionTitle
import com.example.sortmate.ui.components.StatCard
import com.example.sortmate.util.formatPesoShort
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenBills: () -> Unit,
    onOpenChores: () -> Unit,
    onScanBill: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val firstName = state.displayName.substringBefore(' ').ifBlank { "roommate" }
    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
    }
    val currentStatus = state.currentMember?.status ?: PresenceStatus.HOME

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = dateLabel.uppercase(Locale.US),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Hello, $firstName",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = state.household?.name ?: "Your household",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PresenceCard(
                status = currentStatus,
                memberCount = state.members.size,
                homeCount = state.members.count { it.status == PresenceStatus.HOME },
                onStatusChange = viewModel::setPresence,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "Your share",
                    value = formatPesoShort(state.myShareCents),
                    icon = Icons.Rounded.Wallet,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Open bills",
                    value = formatPesoShort(state.outstandingCents),
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.secondary,
                )
                StatCard(
                    label = "Chores left",
                    value = state.openChores.size.toString(),
                    icon = Icons.Rounded.Checklist,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        item {
            Column {
                SectionTitle(title = "Roommates")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    state.members.forEach { member ->
                        Column(
                            modifier = Modifier.width(64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(name = member.name, status = member.status, size = 46)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = member.name.substringBefore(' '),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = member.status.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionTitle(
                    title = "Bills due soon",
                    trailing = {
                        TextButton(onClick = onOpenBills) { Text("See all") }
                    },
                )
                Spacer(Modifier.height(4.dp))
                val upcoming = state.openBills.take(3)
                if (upcoming.isEmpty()) {
                    EmptyState(
                        title = "No open bills",
                        body = "Scan a grocery receipt and SortMate will split it for the house.",
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        upcoming.forEach { bill ->
                            BillCard(
                                bill = bill,
                                paidByName = state.members
                                    .firstOrNull { it.id == bill.paidByMemberId }?.name
                                    ?: "Someone",
                                myShareCents = state.currentMember?.let { member ->
                                    bill.shareCents(member.id)
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionTitle(
                    title = "Chore board",
                    trailing = {
                        TextButton(onClick = onOpenChores) { Text("See all") }
                    },
                )
                Spacer(Modifier.height(4.dp))
                val chores = state.dueSoonChores
                if (chores.isEmpty()) {
                    EmptyState(
                        title = "No chores waiting",
                        body = "Assign a task so everyone knows what to do this week.",
                        icon = Icons.Rounded.Checklist,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        chores.forEach { chore ->
                            ChoreCard(
                                chore = chore,
                                assigneeName = state.members
                                    .firstOrNull { it.id == chore.assignedMemberId }?.name
                                    ?: "Anyone",
                                onToggle = { viewModel.toggleChore(chore) },
                            )
                        }
                    }
                }
            }
        }

        item {
            TextButton(onClick = onScanBill, modifier = Modifier.fillMaxWidth()) {
                Text("Scan a receipt now")
            }
        }
    }
}

@Composable
private fun PresenceCard(
    status: PresenceStatus,
    memberCount: Int,
    homeCount: Int,
    onStatusChange: (PresenceStatus) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                RoundedCornerShape(24.dp),
            )
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your presence",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = status.label,
                    modifier = Modifier.markFirstDraw("presence_draw status=$status"),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = when (status) {
                        PresenceStatus.HOME -> "Inside your home geofence."
                        PresenceStatus.CAMPUS -> "Studying on campus."
                        PresenceStatus.AWAY -> "Outside the home boundary."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PresencePill(status)
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresenceStatus.entries.forEach { option ->
                FilterChip(
                    selected = status == option,
                    onClick = { onStatusChange(option) },
                    label = {
                        Text(
                            text = option.label.removePrefix("At ").removePrefix("On "),
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Checklist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$homeCount of $memberCount roommates at home",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
