package com.rohan.debtsync.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.debtsync.R
import com.rohan.debtsync.security.TotpManager
import com.rohan.debtsync.ui.theme.LocalDebtPalette
import kotlinx.coroutines.delay

@Composable
fun AuthGateScreen(
    vm: DebtViewModel,
    isDark: Boolean
) {
    val palette = LocalDebtPalette.current
    var secondsLeft by remember { mutableIntStateOf(TotpManager.secondsRemainingInStep()) }

    LaunchedEffect(Unit) {
        while (true) {
            secondsLeft = TotpManager.secondsRemainingInStep()
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_brand_logo),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
                Column {
                    Text("Debt Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = palette.textPrimary)
                    Text("Authenticator Protected", fontSize = 11.sp, color = palette.textSecondary)
                }
            }
            IconButton(onClick = { vm.toggleTheme(isDark) }) {
                Icon(
                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle theme",
                    tint = palette.textPrimary
                )
            }
        }

        if (!vm.isAuthConfigured) {
            AuthenticatorSetupCard(vm = vm, secondsLeft = secondsLeft)
        } else {
            AuthenticatorLoginCard(vm = vm, secondsLeft = secondsLeft)
        }
    }
}

@Composable
private fun AuthenticatorSetupCard(
    vm: DebtViewModel,
    secondsLeft: Int
) {
    val palette = LocalDebtPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var pendingSecret by remember { mutableStateOf(TotpManager.generateSecretBase32()) }
    var recoveryCode by remember { mutableStateOf(TotpManager.generateRecoveryCode()) }
    var codeInput by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val otpUri = remember(pendingSecret) { TotpManager.buildOtpAuthUri(pendingSecret) }
    val qrBitmap = remember(otpUri) { TotpManager.createQrBitmap(otpUri, 440) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        border = BorderStroke(1.dp, palette.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = palette.accent, modifier = Modifier.size(34.dp))
            Text(
                "Sync Your Authenticator App",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = palette.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "Link Google Authenticator, Microsoft Authenticator, or Authy before adding your loans. Without a live 6-digit code, nobody can log in.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            if (qrBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "Authenticator QR Code",
                        modifier = Modifier
                            .padding(12.dp)
                            .size(180.dp)
                    )
                }
            }

            // Manual Setup Key
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.subtleSurface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Setup Key (Manual Entry)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textSecondary)
                Text(
                    TotpManager.formatSecretForDisplay(pendingSecret),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(pendingSecret))
                            isError = false
                            feedback = "Secret key copied! Paste it into your Authenticator app."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Copy Key", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(otpUri)))
                            } catch (_: Exception) {
                                isError = true
                                feedback = "No authenticator app caught the link — copy the key or scan the QR instead."
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Open App", fontSize = 12.sp)
                    }
                }
            }

            // One-time recovery backup code
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.warningBg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Backup Recovery Code (Save this!)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.warning)
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(recoveryCode))
                            isError = false
                            feedback = "Recovery code copied."
                        }
                    ) {
                        Text("Copy", fontSize = 11.sp)
                    }
                }
                Text(
                    recoveryCode,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary
                )
            }

            // Live 30s timer bar
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { secondsLeft / 30f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
                    color = palette.accent,
                    trackColor = palette.divider
                )
                Text("Code refreshes in ${secondsLeft}s", fontSize = 11.sp, color = palette.textSecondary)
            }

            OutlinedTextField(
                value = codeInput,
                onValueChange = { raw ->
                    codeInput = raw.filter { it.isDigit() }.take(6)
                },
                label = { Text("Enter 6-digit code from Authenticator") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            feedback?.let { msg ->
                Text(
                    msg,
                    fontSize = 12.sp,
                    color = if (isError) palette.danger else palette.success,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = {
                    val ok = vm.completeAuthenticatorSetup(pendingSecret, recoveryCode, codeInput)
                    if (!ok) {
                        isError = true
                        feedback = "Invalid 6-digit code. Wait for the next code and try again."
                    }
                },
                enabled = codeInput.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify & Lock App")
            }

            TextButton(
                onClick = {
                    pendingSecret = TotpManager.generateSecretBase32()
                    recoveryCode = TotpManager.generateRecoveryCode()
                    codeInput = ""
                    feedback = null
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Generate New Key", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AuthenticatorLoginCard(
    vm: DebtViewModel,
    secondsLeft: Int
) {
    val palette = LocalDebtPalette.current
    var codeInput by remember { mutableStateOf("") }
    var useRecovery by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        border = BorderStroke(1.dp, palette.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (useRecovery) Icons.Default.Key else Icons.Default.Lock,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(38.dp)
            )
            Text(
                if (useRecovery) "Unlock with Recovery Code" else "Enter Authenticator Code",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = palette.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                if (useRecovery) {
                    "Enter the 8-character recovery code you saved during Authenticator setup."
                } else {
                    "Open your Authenticator app and enter the 6-digit code to unlock your loans."
                },
                fontSize = 12.sp,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )

            if (!useRecovery) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { secondsLeft / 30f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
                        color = palette.accent,
                        trackColor = palette.divider
                    )
                    Text("Current code window: ${secondsLeft}s", fontSize = 11.sp, color = palette.textSecondary)
                }
            }

            OutlinedTextField(
                value = codeInput,
                onValueChange = { raw ->
                    errorText = null
                    codeInput = if (useRecovery) {
                        raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(9)
                    } else {
                        raw.filter { it.isDigit() }.take(6)
                    }
                },
                label = { Text(if (useRecovery) "Recovery Code (XXXX-XXXX)" else "6-Digit Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (useRecovery) KeyboardType.Ascii else KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            errorText?.let { msg ->
                Text(msg, fontSize = 12.sp, color = palette.danger, textAlign = TextAlign.Center)
            }

            Button(
                onClick = {
                    val ok = if (useRecovery) {
                        vm.unlockWithRecoveryCode(codeInput)
                    } else {
                        vm.unlockWithTotp(codeInput)
                    }
                    if (!ok) {
                        errorText = if (useRecovery) {
                            "Invalid recovery code."
                        } else {
                            "Incorrect or expired authentication code."
                        }
                    }
                },
                enabled = if (useRecovery) codeInput.replace("-", "").length >= 8 else codeInput.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify & Log In")
            }

            TextButton(
                onClick = {
                    useRecovery = !useRecovery
                    codeInput = ""
                    errorText = null
                }
            ) {
                Text(
                    if (useRecovery) "Use 6-digit Authenticator code instead" else "Lost authenticator? Use recovery code",
                    fontSize = 12.sp
                )
            }
        }
    }
}
