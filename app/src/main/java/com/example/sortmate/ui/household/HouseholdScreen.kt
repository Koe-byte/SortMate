package com.example.sortmate.ui.household

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sortmate.data.local.entity.HouseholdEntity
import com.example.sortmate.data.local.entity.MemberEntity
import com.example.sortmate.domain.QuietHours
import com.example.sortmate.location.hasFineLocation
import com.example.sortmate.ui.components.Avatar
import com.example.sortmate.ui.components.PresencePill
import com.example.sortmate.ui.components.ScreenHeader
import com.example.sortmate.ui.components.SectionCard
import com.example.sortmate.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HouseholdScreen(viewModel: HouseholdViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showRename by remember { mutableStateOf(false) }
    var showQuietHours by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    val household = state.household
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasFineLocation(context)
        if (granted) viewModel.useCurrentLocation()
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        viewModel.dismissMessage()
        snackbarHostState.showSnackbar(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Household",
                    eyebrow = "Home base",
                    subtitle = "Roommates, quiet hours, and the home geofence",
                )
            }

            if (!state.firebaseAvailable) {
                item {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                                RoundedCornerShape(18.dp),
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Local demo mode",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Add app/google-services.json and rebuild to sync with roommates in real time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    if (household != null) {
                        HouseholdCodeCard(
                            household = household,
                            memberCount = state.members.size,
                            onCopy = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("SortMate join code", household.joinCode),
                                )
                                scope.launch { snackbarHostState.showSnackbar("Join code copied") }
                            },
                            onRename = { showRename = true },
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionCard {
                        SectionTitle(
                            title = "Roommates",
                            trailing = {
                                Text(
                                    text = "${state.members.size} total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        state.members.forEachIndexed { index, member ->
                            MemberRow(
                                member = member,
                                isCurrent = member.id == state.currentMember?.id,
                            )
                            if (index != state.members.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    LocationCard(
                        household = household,
                        isLocating = state.isLocating,
                        onUseCurrentLocation = {
                            if (hasFineLocation(context)) {
                                viewModel.useCurrentLocation()
                            } else {
                                fineLocationLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                        onRefreshGeofence = viewModel::registerSavedGeofence,
                        onRemoveGeofence = viewModel::removeGeofence,
                        onOpenSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                        onSimulateArrival = { viewModel.simulateBoundary(com.example.sortmate.domain.model.PresenceStatus.HOME) },
                        onSimulateExit = { viewModel.simulateBoundary(com.example.sortmate.domain.model.PresenceStatus.AWAY) },
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    QuietHoursCard(
                        household = household,
                        onEdit = { showQuietHours = true },
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionCard {
                        SectionTitle(title = "Notifications")
                        Spacer(Modifier.height(4.dp))
                        NotificationRow(
                            title = "Bill reminders",
                            subtitle = "Daily check at 8:00 AM",
                            checked = state.billReminders,
                            onCheckedChange = {
                                viewModel.setNotificationSetting(NotificationSetting.BILLS, it)
                            },
                        )
                        NotificationRow(
                            title = "Chore reminders",
                            subtitle = "Daily check at 6:00 PM",
                            checked = state.choreReminders,
                            onCheckedChange = {
                                viewModel.setNotificationSetting(NotificationSetting.CHORES, it)
                            },
                        )
                        NotificationRow(
                            title = "Quiet hours",
                            subtitle = "Tell me when study hours begin",
                            checked = state.quietHoursAlerts,
                            onCheckedChange = {
                                viewModel.setNotificationSetting(NotificationSetting.QUIET_HOURS, it)
                            },
                        )
                        NotificationRow(
                            title = "Household activity",
                            subtitle = "Geofence and presence changes",
                            checked = state.activityAlerts,
                            onCheckedChange = {
                                viewModel.setNotificationSetting(NotificationSetting.ACTIVITY, it)
                            },
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionCard {
                        SectionTitle(title = "Danger zone")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Resetting removes the local household, bills, chores, and session. Firestore data is untouched.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showReset = true },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Reset local data",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "SortMate · CS312 Mobile Computing · Group 4",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showRename && household != null) {
        RenameHouseholdDialog(
            initialName = household.name,
            onDismiss = { showRename = false },
            onConfirm = {
                viewModel.updateHouseholdName(it)
                showRename = false
            },
        )
    }

    if (showQuietHours && household != null) {
        QuietHoursDialog(
            household = household,
            onDismiss = { showQuietHours = false },
            onConfirm = { start, end ->
                viewModel.setQuietHours(start, end)
                showQuietHours = false
            },
        )
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset everything?") },
            text = {
                Text("This clears the local household and returns to onboarding. Cloud data stays in Firestore.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReset = false
                        viewModel.resetLocalData()
                    },
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HouseholdCodeCard(
    household: HouseholdEntity,
    memberCount: Int,
    onCopy: () -> Unit,
    onRename: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOUSEHOLD",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = household.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$memberCount roommate${if (memberCount == 1) "" else "s"} · " +
                            "${household.geofenceRadiusMeters.toInt()} m home radius",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Rename household",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "JOIN CODE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    )
                    Text(
                        text = household.joinCode,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy join code",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: MemberEntity, isCurrent: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = member.name, status = member.status, size = 42)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "YOU",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                text = member.role,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PresencePill(member.status)
    }
}

@Composable
private fun LocationCard(
    household: HouseholdEntity?,
    isLocating: Boolean,
    onUseCurrentLocation: () -> Unit,
    onRefreshGeofence: () -> Unit,
    onRemoveGeofence: () -> Unit,
    onOpenSettings: () -> Unit,
    onSimulateArrival: () -> Unit,
    onSimulateExit: () -> Unit,
) {
    SectionCard {
        SectionTitle(
            title = "Home geofence",
            trailing = {
                Icon(
                    imageVector = if (household?.homeLatitude != null) {
                        Icons.Rounded.LocationOn
                    } else {
                        Icons.Rounded.LocationOff
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Spacer(Modifier.height(6.dp))

        val latitude = household?.homeLatitude
        val longitude = household?.homeLongitude
        if (latitude != null && longitude != null) {
            Text(
                text = String.format(Locale.US, "%.5f, %.5f", latitude, longitude),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Geofence radius ${household.geofenceRadiusMeters.toInt()} m. " +
                    "Entering marks you At home; leaving marks you Away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "No home boundary yet. Stand at home, then save your current location. " +
                    "Background location permission is required for automatic updates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onUseCurrentLocation,
            enabled = !isLocating,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isLocating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Finding you…")
            } else {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (latitude == null) "Save current location" else "Update location")
            }
        }

        if (latitude != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefreshGeofence,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Refresh", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onRemoveGeofence,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Remove", maxLines = 1)
                }
            }
        }

        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open location permissions")
        }
        Text(
            text = "Choose \"Allow all the time\" in Settings so the geofence can update presence while SortMate is closed.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Demo the pipeline", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Simulate a boundary crossing without walking outside.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSimulateArrival, modifier = Modifier.weight(1f)) {
                Text("Arrive home")
            }
            TextButton(onClick = onSimulateExit, modifier = Modifier.weight(1f)) {
                Text("Leave home")
            }
        }
    }
}

@Composable
private fun QuietHoursCard(
    household: HouseholdEntity?,
    onEdit: () -> Unit,
) {
    SectionCard {
        SectionTitle(
            title = "Quiet hours",
            trailing = {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        Spacer(Modifier.height(6.dp))
        if (household == null) {
            Text(
                text = "Set a household first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "${QuietHours.format(household.quietStartMinutes)} – " +
                    QuietHours.format(household.quietEndMinutes),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Lights out for noisy chores and calls. Crossing midnight is supported.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onEdit) { Text("Edit quiet hours") }
        }
    }
}

@Composable
private fun NotificationRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RenameHouseholdDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename household") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Household name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun QuietHoursDialog(
    household: HouseholdEntity,
    onDismiss: () -> Unit,
    onConfirm: (startMinutes: Int, endMinutes: Int) -> Unit,
) {
    var startText by remember { mutableStateOf(formatForInput(household.quietStartMinutes)) }
    var endText by remember { mutableStateOf(formatForInput(household.quietEndMinutes)) }
    val start = parseTime(startText)
    val end = parseTime(endText)
    val valid = start != null && end != null && start != end

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiet hours") },
        text = {
            Column {
                Text(
                    text = "Use 24-hour time, for example 22:00 and 06:00.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.take(5) },
                        label = { Text("Starts") },
                        singleLine = true,
                        isError = parseTime(startText) == null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.take(5) },
                        label = { Text("Ends") },
                        singleLine = true,
                        isError = parseTime(endText) == null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Enter two different times in HH:mm format.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (start != null && end != null && start != end) onConfirm(start, end)
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatForInput(minutes: Int): String =
    String.format(Locale.US, "%02d:%02d", (minutes / 60) % 24, minutes % 60)

private fun parseTime(value: String): Int? {
    val parts = value.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}
