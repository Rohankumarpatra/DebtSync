package com.rohan.debtsync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.model.CurrencyMode
import com.rohan.debtsync.model.formatMoney
import com.rohan.debtsync.reminder.DueEmiReminder
import com.rohan.debtsync.ui.theme.EmiGoDialog
import com.rohan.debtsync.ui.theme.LocalDebtPalette
import com.rohan.debtsync.update.AppUpdateInfo

@Composable
fun EmiReminderPopupDialog(
    reminders: List<DueEmiReminder>,
    currency: CurrencyMode,
    leadDays: Int,
    onMarkPaid: (loanId: String, monthKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalDebtPalette.current

    EmiGoDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Upcoming EMI Reminder", fontWeight = FontWeight.Bold)
                Text(
                    "${reminders.size} EMI(s) due within $leadDays day(s) or overdue",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            reminders.forEach { item ->
                val isUrgent = item.daysUntilDue <= 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isUrgent) palette.warningBg else palette.subtleSurface)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            item.platform,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            "${formatMoney(item.amount, currency)} · ${item.dueDateLabel}",
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                        Text(
                            item.urgencyLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isUrgent) palette.danger else palette.warning
                        )
                    }
                    Button(
                        onClick = { onMarkPaid(item.loanId, item.monthKey) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.success,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Paid", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityAndReminderDialog(
    vm: DebtViewModel,
    onReconfigureAuthenticator: () -> Unit,
    onLockNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalDebtPalette.current
    val dayChoices = listOf(1, 2, 3, 5, 7)

    EmiGoDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminders & Security", fontWeight = FontWeight.Bold) },
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("EMI Due Reminders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
                    Text("Pop up & notify before due date", fontSize = 11.sp, color = palette.textSecondary)
                }
                Switch(
                    checked = vm.remindersEnabled,
                    onCheckedChange = { vm.updateReminderEnabled(it) }
                )
            }

            if (vm.remindersEnabled) {
                Text("Remind me how many days before due date?", fontSize = 12.sp, color = palette.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayChoices.forEach { d ->
                        val selected = vm.reminderLeadDays == d
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) palette.accentSoftBg else palette.subtleSurface)
                                .border(
                                    1.dp,
                                    if (selected) palette.accent else palette.cardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { vm.updateReminderLeadDays(d) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "${d}d",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) palette.accent else palette.textPrimary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.divider)

            Text("Authenticator Lock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onReconfigureAuthenticator()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Re-sync New Authenticator Key")
            }
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onLockNow()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lock App Now")
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    update: AppUpdateInfo,
    currentVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalDebtPalette.current
    EmiGoDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Update Available (${update.latestVersion})", fontWeight = FontWeight.Bold)
                Text("Current version: v$currentVersion", fontSize = 12.sp, color = palette.textSecondary)
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("Download Update")
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                update.releaseNotes,
                fontSize = 13.sp,
                color = palette.textPrimary
            )
        }
    }
}

@Composable
fun EmiGoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalDebtPalette.current
    EmiGoDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    fontWeight = FontWeight.Bold,
                    color = if (isDestructive) palette.danger else palette.accent
                )
            }
        }
    ) {
        Text(message, fontSize = 13.sp, color = palette.textSecondary)
    }
}
