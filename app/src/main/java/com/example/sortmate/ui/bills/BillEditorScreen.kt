package com.example.sortmate.ui.bills

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.sortmate.ui.components.InfoBanner
import com.example.sortmate.ui.components.ScreenHeader
import com.example.sortmate.ui.components.SectionCard
import com.example.sortmate.ui.components.SectionTitle
import com.example.sortmate.util.formatPeso
import kotlinx.coroutines.delay

@Composable
fun BillEditorScreen(
    viewModel: BillEditorViewModel,
    state: BillEditorUiState,
    onBack: () -> Unit,
) {
    var showScanner by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            delay(750)
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        ScreenHeader(
            title = if (state.saved) "Bill saved" else "New bill",
            eyebrow = "Receipt pipeline",
            subtitle = "CameraX → ML Kit OCR → Room → Supabase",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.error != null) {
                InfoBanner(
                    text = state.error.orEmpty(),
                    icon = Icons.Rounded.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            ReceiptScanCard(
                state = state,
                onScan = { showScanner = true },
            )

            SectionCard {
                SectionTitle(title = "Bill details")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Weekly groceries") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.merchant,
                    onValueChange = viewModel::setMerchant,
                    label = { Text("Merchant (optional)") },
                    placeholder = { Text("e.g. Puregold") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.totalText,
                    onValueChange = viewModel::setTotal,
                    label = { Text("Total amount") },
                    placeholder = { Text("0.00") },
                    prefix = { Text("₱") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.ocrResult?.totalCents != null) {
                    Text(
                        text = "Detected by OCR · ${state.ocrResult.confidence}% confidence",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            SectionCard {
                SectionTitle(title = "Paid by")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.members.forEach { member ->
                        FilterChip(
                            selected = state.paidByMemberId == member.id,
                            onClick = { viewModel.setPaidBy(member.id) },
                            label = { Text(member.name.substringBefore(' ')) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionTitle(title = "Due")
                Spacer(Modifier.height(8.dp))
                var dueChoice by remember { mutableIntStateOf(2) }
                val dueOptions = listOf(
                    0L to "Today",
                    3L to "3 days",
                    7L to "1 week",
                    14L to "2 weeks",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dueOptions.forEachIndexed { index, option ->
                        FilterChip(
                            selected = dueChoice == index,
                            onClick = {
                                dueChoice = index
                                viewModel.setDueInDays(option.first)
                            },
                            label = { Text(option.second) },
                        )
                    }
                }
            }

            SectionCard {
                SectionTitle(
                    title = "Split preview",
                    trailing = {
                        Text(
                            text = "EQUAL",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.totalCents?.let(::formatPeso) ?: "₱0.00",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = if (state.splitCount > 1) {
                        "Divided across ${state.splitCount} roommates · " +
                            "${formatPeso(state.perPersonCents)} each"
                    } else {
                        "Add a roommate to split this bill."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatPeso(state.totalCents?.div(state.splitCount) ?: 0L),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving && !state.saved && state.household != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                when {
                    state.saved -> {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Saved")
                    }
                    state.isSaving -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…")
                    }
                    else -> {
                        Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save and split")
                    }
                }
            }

            Text(
                text = "SortMate records who paid and how the total is shared. No payment is processed in the app.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showScanner) {
        ReceiptScannerDialog(
            onImageCaptured = { uri ->
                showScanner = false
                viewModel.processImage(uri)
            },
            onDismiss = { showScanner = false },
        )
    }
}

@Composable
private fun ReceiptScanCard(
    state: BillEditorUiState,
    onScan: () -> Unit,
) {
    val bitmap = remember(state.receiptPath) {
        state.receiptPath?.let { path ->
            runCatching {
                BitmapFactory.decodeFile(
                    path,
                    BitmapFactory.Options().apply { inSampleSize = 4 },
                )
            }.getOrNull()
        }
    }

    SectionCard {
        SectionTitle(
            title = "Receipt",
            trailing = {
                Text(
                    text = when {
                        state.isProcessing -> "READING"
                        state.ocrResult != null -> "SCANNED"
                        else -> "OPTIONAL"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )

        if (bitmap != null) {
            Spacer(Modifier.height(10.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured receipt",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "No receipt attached",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.isProcessing) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Running on-device ML Kit text recognition…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val result = state.ocrResult
        if (result != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = buildString {
                    append("Merchant: ")
                    append(result.merchant ?: "not detected")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${result.items.size} lines parsed · ${result.confidence}% confidence",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onScan,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.ocrResult == null) "Scan receipt with camera" else "Retake photo")
        }
    }
}
