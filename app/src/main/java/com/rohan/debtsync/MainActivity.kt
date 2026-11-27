package com.rohan.debtsync

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rohan.debtsync.model.LoanAccount
import com.rohan.debtsync.ui.AddLoanDialog
import com.rohan.debtsync.ui.AuthGateScreen
import com.rohan.debtsync.ui.CloudSyncDialog
import com.rohan.debtsync.ui.DebtViewModel
import com.rohan.debtsync.ui.EmiReminderPopupDialog
import com.rohan.debtsync.ui.MatrixScreen
import com.rohan.debtsync.ui.OverviewScreen
import com.rohan.debtsync.ui.SecurityAndReminderDialog
import com.rohan.debtsync.ui.SimulatorScreen
import com.rohan.debtsync.ui.SmsSyncDialog
import com.rohan.debtsync.ui.UpdateAvailableDialog
import com.rohan.debtsync.ui.EmiGoConfirmDialog
import com.rohan.debtsync.ui.theme.DebtSyncTheme
import com.rohan.debtsync.ui.theme.GeminiBackground
import com.rohan.debtsync.ui.theme.LocalDebtPalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots, screen recording, and recents preview thumbnails
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        // Mitigate tapjacking from malicious screen overlays
        window.decorView.filterTouchesWhenObscured = true
        enableEdgeToEdge()
        setContent {
            DebtSyncRoot()
        }
    }
}

@Composable
fun DebtSyncRoot(vm: DebtViewModel = viewModel()) {
    val isDark = vm.darkThemeOverride ?: isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var stoppedAtMs by remember { mutableLongStateOf(0L) }
    DisposableEffect(lifecycleOwner, vm.isAuthConfigured, vm.isUnlocked) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stoppedAtMs = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    if (vm.isAuthConfigured && vm.isUnlocked && stoppedAtMs > 0L) {
                        val elapsed = System.currentTimeMillis() - stoppedAtMs
                        if (elapsed > 20_000L) {
                            vm.lockApp()
                        } else {
                            vm.refreshReminders(openPopupIfDue = true)
                        }
                    }
                    stoppedAtMs = 0L
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DebtSyncTheme(isDark = isDark) {
        GeminiBackground {
            if (!vm.isUnlocked) {
                AuthGateScreen(vm = vm, isDark = isDark)
            } else {
                DebtSyncApp(vm = vm, isDark = isDark)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtSyncApp(vm: DebtViewModel, isDark: Boolean) {
    val context = LocalContext.current
    val palette = LocalDebtPalette.current
    val snackbarHostState = remember { SnackbarHostState() }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<LoanAccount?>(null) }
    var loanToDelete by remember { mutableStateOf<LoanAccount?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSmsSyncDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { grants ->
            hasSmsPermission = grants[Manifest.permission.READ_SMS] == true
            if (hasSmsPermission) {
                vm.toggleSmsAutoSync(true)
            }
        }
    )

    LaunchedEffect(vm.statusMessage) {
        vm.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_logo),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                        Text("EmiGo", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.barBg,
                    titleContentColor = palette.textPrimary,
                    actionIconContentColor = palette.textPrimary
                ),
                actions = {
                    IconButton(onClick = { vm.openReminderPopup() }) {
                        if (vm.dueReminders.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${vm.dueReminders.size}") } }) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = "EMI Reminders")
                            }
                        } else {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "EMI Reminders")
                        }
                    }
                    IconButton(onClick = { vm.toggleTheme(isDark) }) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDark) "Switch to Light Theme" else "Switch to Dark Theme"
                        )
                    }
                    TextButton(onClick = { vm.toggleCurrency() }) {
                        Text(vm.currency.label, fontWeight = FontWeight.Bold, color = palette.accent)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("SMS Auto-Sync") },
                                onClick = {
                                    menuExpanded = false
                                    if (hasSmsPermission) vm.scanSmsForEmis()
                                    showSmsSyncDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mobile Cloud Sync") },
                                onClick = {
                                    menuExpanded = false
                                    showCloudSyncDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reminders & Security") },
                                onClick = {
                                    menuExpanded = false
                                    showSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Check for Updates") },
                                onClick = {
                                    menuExpanded = false
                                    vm.checkForUpdates(manual = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lock App") },
                                onClick = {
                                    menuExpanded = false
                                    vm.lockApp()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Data") },
                                onClick = {
                                    menuExpanded = false
                                    showClearConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        editingLoan = null
                        showAddDialog = true
                    },
                    containerColor = palette.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Loan")
                }
            }
        },
        bottomBar = {
            val navColors = NavigationBarItemDefaults.colors(
                selectedIconColor = palette.accent,
                selectedTextColor = palette.accent,
                indicatorColor = palette.accentSoftBg,
                unselectedIconColor = palette.textSecondary,
                unselectedTextColor = palette.textSecondary
            )
            NavigationBar(containerColor = palette.barBg) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Overview") },
                    label = { Text("Overview") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.TableChart, contentDescription = "Matrix") },
                    label = { Text("Matrix") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Simulator") },
                    label = { Text("Simulator") },
                    colors = navColors
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 32 })
                        .togetherWith(fadeOut(tween(150)))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    0 -> OverviewScreen(
                        vm = vm,
                        onAddLoan = {
                            editingLoan = null
                            showAddDialog = true
                        },
                        onEditLoan = { loan ->
                            editingLoan = loan
                            showAddDialog = true
                        },
                        onDeleteLoan = { loan -> loanToDelete = loan }
                    )
                    1 -> MatrixScreen(vm = vm)
                    2 -> SimulatorScreen(vm = vm)
                }
            }
        }
    }

    if (vm.showReminderPopup && vm.dueReminders.isNotEmpty()) {
        EmiReminderPopupDialog(
            reminders = vm.dueReminders,
            currency = vm.currency,
            leadDays = vm.reminderLeadDays,
            onMarkPaid = { loanId, monthKey -> vm.togglePayment(loanId, monthKey) },
            onDismiss = { vm.dismissReminderPopup() }
        )
    }

    vm.availableUpdate?.let { update ->
        UpdateAvailableDialog(
            update = update,
            currentVersion = BuildConfig.VERSION_NAME,
            onDownload = {
                vm.dismissUpdateDialog()
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
                } catch (_: Exception) {
                }
            },
            onDismiss = { vm.dismissUpdateDialog() }
        )
    }

    if (showSettingsDialog) {
        SecurityAndReminderDialog(
            vm = vm,
            onReconfigureAuthenticator = { vm.beginAuthResync() },
            onLockNow = { vm.lockApp() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showSmsSyncDialog) {
        SmsSyncDialog(
            vm = vm,
            currency = vm.currency,
            hasSmsPermission = hasSmsPermission,
            onRequestSmsPermission = {
                smsPermLauncher.launch(
                    arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                )
            },
            onDismiss = { showSmsSyncDialog = false }
        )
    }

    if (showCloudSyncDialog) {
        CloudSyncDialog(
            vm = vm,
            onDismiss = { showCloudSyncDialog = false }
        )
    }

    if (showAddDialog) {
        AddLoanDialog(
            existingLoan = editingLoan,
            currency = vm.currency,
            onDismiss = { showAddDialog = false },
            onSave = { platform, dueDay, amounts ->
                vm.saveLoan(editingLoan?.id, platform, dueDay, amounts)
                showAddDialog = false
            }
        )
    }

    loanToDelete?.let { target ->
        EmiGoConfirmDialog(
            title = "Delete ${target.platform}?",
            message = "This will remove the loan account and all of its scheduled EMIs.",
            confirmLabel = "Delete",
            onConfirm = {
                vm.deleteLoan(target.id)
                loanToDelete = null
            },
            onDismiss = { loanToDelete = null }
        )
    }

    if (showClearConfirm) {
        EmiGoConfirmDialog(
            title = "Clear All Loan Data?",
            message = "This removes all saved loan accounts and starts with an empty tracker.",
            confirmLabel = "Clear All",
            onConfirm = {
                vm.clearAllLoans()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}
