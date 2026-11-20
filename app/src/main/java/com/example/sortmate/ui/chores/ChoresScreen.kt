package com.example.sortmate.ui.chores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sortmate.ui.components.ChoreCard
import com.example.sortmate.ui.components.EmptyState
import com.example.sortmate.ui.components.ScreenHeader
import com.example.sortmate.ui.components.SectionTitle
import com.example.sortmate.util.daysFromNow

@Composable
fun ChoresScreen(viewModel: ChoresViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = "Chores",
                eyebrow = "Shared rhythm",
                subtitle = "${state.openChores.size} open · ${state.doneChores.size} done this week",
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Rounded.AddTask, contentDescription = "Add chore")
                    }
                },
            )
        }

        if (state.chores.isEmpty()) {
            item {
                EmptyState(
                    title = "No chores yet",
                    body = "Add the trash, the dishes, or the weekly sweep. Everyone sees the same board.",
                    icon = Icons.Rounded.Checklist,
                    action = {
                        TextButton(onClick = { showAddDialog = true }) { Text("Add the first chore") }
                    },
                )
            }
        } else {
            if (state.openChores.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionTitle(title = "To do · ${state.openChores.size}")
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.openChores.forEach { chore ->
                                ChoreCard(
                                    chore = chore,
                                    assigneeName = state.memberName(chore.assignedMemberId),
                                    onToggle = { viewModel.toggle(chore) },
                                    onDelete = { viewModel.delete(chore) },
                                )
                            }
                        }
                    }
                }
            }

            if (state.doneChores.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(8.dp))
                        SectionTitle(title = "Completed · ${state.doneChores.size}")
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.doneChores.forEach { chore ->
                                ChoreCard(
                                    chore = chore,
                                    assigneeName = state.memberName(chore.assignedMemberId),
                                    onToggle = { viewModel.toggle(chore) },
                                    onDelete = { viewModel.delete(chore) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddChoreDialog(
            members = state.members,
            onDismiss = { showAddDialog = false },
            onAdd = { title, assignee, dueAt ->
                viewModel.addChore(title, assignee, dueAt)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddChoreDialog(
    members: List<com.example.sortmate.data.local.entity.MemberEntity>,
    onDismiss: () -> Unit,
    onAdd: (title: String, assigneeId: String?, dueAt: Long?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var assigneeId by remember { mutableStateOf<String?>(null) }
    var dueChoice by remember { mutableStateOf(1) }

    val dueOptions = listOf(
        "Today" to daysFromNow(0),
        "Tomorrow" to daysFromNow(1),
        "In 3 days" to daysFromNow(3),
        "No date" to null,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text("New chore") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What needs doing?") },
                    placeholder = { Text("e.g. Clean the kitchen") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Assign to",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = assigneeId == null,
                        onClick = { assigneeId = null },
                        label = { Text("Anyone") },
                    )
                    members.forEach { member ->
                        FilterChip(
                            selected = assigneeId == member.id,
                            onClick = { assigneeId = member.id },
                            label = { Text(member.name.substringBefore(' ')) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Due",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dueOptions.forEachIndexed { index, option ->
                        FilterChip(
                            selected = dueChoice == index,
                            onClick = { dueChoice = index },
                            label = { Text(option.first) },
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title.trim(), assigneeId, dueOptions[dueChoice].second) },
                enabled = title.isNotBlank(),
            ) {
                Text("Add chore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
