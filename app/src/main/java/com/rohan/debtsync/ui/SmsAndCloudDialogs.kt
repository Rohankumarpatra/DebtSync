package com.rohan.debtsync.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.cloud.CloudSyncManager
import com.rohan.debtsync.model.CurrencyMode
import com.rohan.debtsync.model.PaymentStatus
import com.rohan.debtsync.model.formatMoney
import com.rohan.debtsync.sms.DetectedSmsEmi
import com.rohan.debtsync.ui.theme.EmiGoDialog
import com.rohan.debtsync.ui.theme.LocalDebtPalette

@Composable
fun SmsSyncDialog(
    vm: DebtViewModel,
    currency: CurrencyMode,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalDebtPalette.current
    val detected = vm.detectedSmsEmis
    val selectedIds = remember(detected) {
        mutableStateMapOf<String, Boolean>().apply {
            detected.forEach { put(it.id, true) }
        }
    }

    EmiGoDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("SMS Auto-Sync", fontWeight = FontWeight.Bold)
                Text(
                    "Reads lender EMI alerts locally on your phone (never uploaded)",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
            if (hasSmsPermission && detected.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val chosen = detected.filter { selectedIds[it.id] == true }
                        vm.applyDetectedSms(chosen)
                        onDismiss()
                    },
                    enabled = detected.any { selectedIds[it.id] == true },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                ) {
                    Text("Apply Selected")
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasSmsPermission) {
                Text(
                    "Grant SMS permission so EmiGo can scan lender EMI messages and auto-update Paid/Due statuses in real time.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Button(
                    onClick = onRequestSmsPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow SMS Access")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Live Background SMS Sync", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.textPrimary)
                        Text("Auto-update when new EMI SMS arrives", fontSize = 11.sp, color = palette.textSecondary)
                    }
                    Switch(
                        checked = vm.smsAutoSyncEnabled,
                        onCheckedChange = { vm.toggleSmsAutoSync(it) }
                    )
                }

                OutlinedButton(
                    onClick = { vm.scanSmsForEmis() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan Last 90 Days SMS")
                }

                HorizontalDivider(color = palette.divider)

                if (detected.isEmpty()) {
                    Text(
                        "No EMI SMS alerts detected yet. Tap \"Scan Last 90 Days SMS\" to check your inbox.",
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                } else {
                    Text(
                        "Detected EMI Messages (${detected.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                    detected.forEach { item: DetectedSmsEmi ->
                        val checked = selectedIds[item.id] == true
                        val isPaid = item.status == PaymentStatus.PAID
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPaid) palette.successBg else palette.subtleSurface)
                                .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                                .clickable { selectedIds[item.id] = !checked }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selectedIds[item.id] = it }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${item.platform} · ${formatMoney(item.amount, currency)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = palette.textPrimary
                                )
                                Text(
                                    "${item.monthKey} · ${if (isPaid) "Paid" else "Upcoming"}" +
                                        (item.dueDay?.let { " · Day $it" } ?: ""),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isPaid) palette.success else palette.warning
                                )
                                Text(
                                    item.snippet,
                                    fontSize = 10.sp,
                                    color = palette.textSecondary,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudSyncDialog(
    vm: DebtViewModel,
    onDismiss: () -> Unit
) {
    val palette = LocalDebtPalette.current
    val context = LocalContext.current
    val activity = context as? Activity
    val isFirebaseReady = remember { CloudSyncManager.isFirebaseReady(context) }

    var phoneText by remember { mutableStateOf("") }
    var otpText by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    EmiGoDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Real-Time Mobile Sync", fontWeight = FontWeight.Bold)
                Text(
                    "Sync EMIs across phones & web using your mobile number",
                    fontSize = 11.sp,
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
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isFirebaseReady) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.warningBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Firebase Config Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.warning
                    )
                    Text(
                        "To activate live SMS OTP login & real-time cloud sync across devices, add your google-services.json from Firebase Console into app/ and rebuild.",
                        fontSize = 12.sp,
                        color = palette.textPrimary
                    )
                }
            } else if (vm.cloudPhoneNumber != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.successBg)
                        .border(1.dp, palette.successBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Connected & Syncing Live", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.success)
                    Text("Mobile: ${vm.cloudPhoneNumber}", fontSize = 12.sp, color = palette.textPrimary)
                }
                OutlinedButton(
                    onClick = { vm.pushCloudNow() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync Now")
                }
                OutlinedButton(
                    onClick = { vm.signOutCloud() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out Mobile Sync", color = palette.danger)
                }
            } else {
                OutlinedTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = { Text("Registered Mobile Number") },
                    placeholder = { Text("+919876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                if (verificationId == null) {
                    Button(
                        onClick = {
                            if (activity == null) return@Button
                            busy = true
                            message = null
                            CloudSyncManager.sendOtp(
                                activity = activity,
                                rawPhone = phoneText,
                                onCodeSent = { vId ->
                                    busy = false
                                    verificationId = vId
                                    isError = false
                                    message = "OTP sent via SMS."
                                },
                                onAutoVerified = { phone ->
                                    busy = false
                                    vm.onCloudSignedIn(phone)
                                },
                                onError = { err ->
                                    busy = false
                                    isError = true
                                    message = err
                                }
                            )
                        },
                        enabled = !busy && phoneText.filter { it.isDigit() }.length >= 10,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Sending OTP..." else "Send SMS OTP")
                    }
                } else {
                    OutlinedTextField(
                        value = otpText,
                        onValueChange = { otpText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("6-Digit SMS OTP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val vId = verificationId ?: return@Button
                            busy = true
                            message = null
                            CloudSyncManager.verifyOtp(
                                context = context,
                                verificationId = vId,
                                otp = otpText,
                                onSuccess = { phone ->
                                    busy = false
                                    vm.onCloudSignedIn(phone)
                                },
                                onError = { err ->
                                    busy = false
                                    isError = true
                                    message = err
                                }
                            )
                        },
                        enabled = !busy && otpText.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Verifying..." else "Verify & Enable Live Sync")
                    }
                }

                message?.let { msg ->
                    Text(
                        msg,
                        fontSize = 12.sp,
                        color = if (isError) palette.danger else palette.success
                    )
                }
            }
        }
    }
}
