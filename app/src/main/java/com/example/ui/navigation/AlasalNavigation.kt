package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ElectricMeter
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.local.entity.ArrearsEntity
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SubscriberFullHistory
import com.example.data.repository.InvoiceDetailBundle
import com.example.data.repository.TariffPricePreviewItem
import com.example.service.AppThemeMode
import com.example.service.BackupAndRestoreManager
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.components.AlasalWatermarkLayer
import com.example.ui.components.EmptyStateCard
import com.example.ui.screens.AccountsAndPermissionsScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.CollectionsAndReceiptsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoicesManagementScreen
import com.example.ui.screens.MeterReadingsScreen
import com.example.ui.screens.ReceiptDetailDialog
import com.example.ui.screens.RemindersAndNotificationsScreen
import com.example.ui.screens.ReportsAndStatementsScreen
import com.example.ui.screens.SubscribersManagementScreen
import com.example.ui.screens.SystemDiagnosticsScreen
import com.example.ui.screens.TotalConsumptionScreen
import com.example.ui.screens.WifiSyncAndBackupScreen
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.FeedbackMessage
import com.example.ui.viewmodel.FoundationUiState
import com.example.ui.viewmodel.FoundationViewModel

object AlasalRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_SUBSCRIBERS = "subscribers"
    const val ROUTE_READINGS = "readings"
    const val ROUTE_INVOICES = "invoices"
    const val ROUTE_SYSTEM = "system"
    const val ROUTE_COLLECTIONS = "collections"
    const val ROUTE_REPORTS = "reports"
    const val ROUTE_FIELD_COLLECTION = "field_collection"
    const val ROUTE_REMINDERS = "reminders"
    const val ROUTE_ACCOUNTS = "accounts"
    const val ROUTE_SYNC_BACKUP = "sync_backup"
    const val ROUTE_TOTAL_CONSUMPTION = "total_consumption"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navDestinations = listOf(
    NavDestinationItem(
        route = AlasalRoutes.ROUTE_DASHBOARD,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_item_dashboard"
    ),
    NavDestinationItem(
        route = AlasalRoutes.ROUTE_SUBSCRIBERS,
        labelRes = R.string.nav_subscribers,
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People,
        testTag = "nav_item_subscribers"
    ),
    NavDestinationItem(
        route = AlasalRoutes.ROUTE_READINGS,
        labelRes = R.string.nav_readings,
        selectedIcon = Icons.Filled.ElectricMeter,
        unselectedIcon = Icons.Outlined.ElectricMeter,
        testTag = "nav_item_readings"
    ),
    NavDestinationItem(
        route = AlasalRoutes.ROUTE_INVOICES,
        labelRes = R.string.nav_invoices,
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
        testTag = "nav_item_invoices"
    ),
    NavDestinationItem(
        route = AlasalRoutes.ROUTE_SYSTEM,
        labelRes = R.string.nav_system,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_item_system"
    )
)

@Composable
fun AlasalAppShell(
    uiState: FoundationUiState,
    themeMode: AppThemeMode,
    notificationPrefs: NotificationAndPrintPrefs,
    reminders: List<ReminderEntity>,
    isDarkTheme: Boolean,
    foundationFeedback: FeedbackMessage?,
    billingFeedback: FeedbackMessage?,
    selectedSubscriberHistory: SubscriberFullHistory?,
    tariffPreviewItems: List<TariffPricePreviewItem>,
    meterReadings: List<MeterReadingEntity>,
    invoices: List<InvoiceEntity>,
    collectionsWithReceipts: List<CollectionWithReceipt>,
    arrearsRecords: List<ArrearsEntity>,
    creditTransactions: List<CreditBalanceEntity>,
    selectedInvoiceDetail: InvoiceDetailBundle?,
    selectedReceipt: ReceiptEntity?,
    foundationViewModel: FoundationViewModel,
    billingViewModel: BillingViewModel,
    backupAndRestoreManager: BackupAndRestoreManager? = null,
    openBackupReportOnStart: Boolean = false,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    val activeUser by foundationViewModel.activeUser.collectAsStateWithLifecycle()
    val effectivePermissions by foundationViewModel.effectivePermissions.collectAsStateWithLifecycle()
    val backupsList by foundationViewModel.backupsList.collectAsStateWithLifecycle()
    val backupSessionPrefs by foundationViewModel.backupSessionPrefs.collectAsStateWithLifecycle()
    val lastRestoreResult by foundationViewModel.lastRestoreResult.collectAsStateWithLifecycle()
    val syncConnectionState by foundationViewModel.syncConnectionState.collectAsStateWithLifecycle()
    val syncStatusMessage by foundationViewModel.syncStatusMessage.collectAsStateWithLifecycle()
    val syncLocalIp by foundationViewModel.syncLocalIp.collectAsStateWithLifecycle()
    val syncHostingPort by foundationViewModel.syncHostingPort.collectAsStateWithLifecycle()
    val discoveredSyncPeers by foundationViewModel.discoveredSyncPeers.collectAsStateWithLifecycle()
    val syncLogs by foundationViewModel.syncLogs.collectAsStateWithLifecycle()
    val lastSyncSummary by foundationViewModel.lastSyncSummary.collectAsStateWithLifecycle()
    val isAppLocked by foundationViewModel.isAppLocked.collectAsStateWithLifecycle()
    val lastInteractionAtMs by foundationViewModel.lastInteractionAtMs.collectAsStateWithLifecycle()
    val lastIssuedInvoice by billingViewModel.lastIssuedInvoice.collectAsStateWithLifecycle()

    LaunchedEffect(
        isAppLocked,
        lastInteractionAtMs,
        backupSessionPrefs.appLockEnabled,
        backupSessionPrefs.inactivityLockTimeoutSeconds
    ) {
        if (!isAppLocked && backupSessionPrefs.appLockEnabled && backupSessionPrefs.inactivityLockTimeoutSeconds > 0) {
            delay(backupSessionPrefs.inactivityLockTimeoutSeconds * 1000L)
            foundationViewModel.checkInactivityTimeout()
        }
    }

    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            foundationViewModel.exportSafBackup(uri)
        }
    }

    val navigateToRoute: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateToSecondaryRoute: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        foundationViewModel.recordUserInteraction()
                    }
                }
            }
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                        navDestinations.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            val label = stringResource(item.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToRoute(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(text = label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(modifier = Modifier.testTag("side_navigation_rail")) {
                        navDestinations.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            val label = stringResource(item.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToRoute(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(text = label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                AlasalWatermarkLayer(modifier = Modifier.weight(1f)) {
                    when (uiState) {
                        is FoundationUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is FoundationUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyStateCard(
                                    icon = Icons.Filled.Settings,
                                    title = "تعذر تحميل قاعدة البيانات",
                                    description = uiState.message
                                )
                            }
                        }

                        is FoundationUiState.Ready -> {
                            val subMap = uiState.subscribers.associateBy { it.id }

                            LaunchedEffect(openBackupReportOnStart) {
                                if (openBackupReportOnStart) {
                                    navController.navigate(AlasalRoutes.ROUTE_SYNC_BACKUP) {
                                        launchSingleTop = true
                                    }
                                }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = AlasalRoutes.ROUTE_DASHBOARD,
                                modifier = Modifier.fillMaxSize(),
                                enterTransition = {
                                    slideInHorizontally(animationSpec = tween(250)) { -it / 5 } + fadeIn(tween(220))
                                },
                                exitTransition = {
                                    slideOutHorizontally(animationSpec = tween(250)) { it / 5 } + fadeOut(tween(220))
                                },
                                popEnterTransition = {
                                    slideInHorizontally(animationSpec = tween(250)) { it / 5 } + fadeIn(tween(220))
                                },
                                popExitTransition = {
                                    slideOutHorizontally(animationSpec = tween(250)) { -it / 5 } + fadeOut(tween(220))
                                }
                            ) {
                                composable(AlasalRoutes.ROUTE_DASHBOARD) {
                                    DashboardScreen(
                                        state = uiState,
                                        isDarkTheme = isDarkTheme,
                                        feedback = foundationFeedback,
                                        invoices = invoices,
                                        collectionsWithReceipts = collectionsWithReceipts,
                                        subscribers = uiState.subscribers,
                                        readings = meterReadings,
                                        onDismissFeedback = foundationViewModel::clearFeedback,
                                        onToggleTheme = { foundationViewModel.cycleThemeMode(isDarkTheme) },
                                        onVerifySchema = foundationViewModel::verifyDatabaseIntegrity,
                                        onScheduleAuditWorker = {
                                            foundationViewModel.scheduleImmediateWorkManagerAudit(context)
                                        },
                                        onRequestSafBackup = {
                                            safExportLauncher.launch("alasal2027_backup.json")
                                        },
                                        onNavigateToTotalConsumption = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_TOTAL_CONSUMPTION)
                                        },
                                        onNavigateToCollections = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_COLLECTIONS)
                                        },
                                        onNavigateToReports = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_REPORTS)
                                        },
                                        onNavigateToFieldCollection = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_FIELD_COLLECTION)
                                        },
                                        onNavigateToReminders = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_REMINDERS)
                                        },
                                        onNavigateToReadings = {
                                            navigateToRoute(AlasalRoutes.ROUTE_READINGS)
                                        },
                                        onNavigateToSubscribers = {
                                            navigateToRoute(AlasalRoutes.ROUTE_SUBSCRIBERS)
                                        },
                                        onNavigateToAccounts = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_ACCOUNTS)
                                        },
                                        onNavigateToSyncBackup = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_SYNC_BACKUP)
                                        },
                                        onLockApp = foundationViewModel::lockAppImmediately,
                                        activeUserName = activeUser?.fullName,
                                        onOpenInvoice = { invoiceId ->
                                            billingViewModel.loadInvoiceDetail(invoiceId)
                                            navigateToRoute(AlasalRoutes.ROUTE_INVOICES)
                                        },
                                        onOpenReceipt = billingViewModel::selectReceipt
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_SUBSCRIBERS) {
                                    SubscribersManagementScreen(
                                        state = uiState,
                                        feedback = foundationFeedback,
                                        selectedHistory = selectedSubscriberHistory,
                                        tariffPreviewItems = tariffPreviewItems,
                                        onDismissFeedback = foundationViewModel::clearFeedback,
                                        onSaveSubscriber = foundationViewModel::saveSubscriber,
                                        onDeleteSubscriber = { id ->
                                            foundationViewModel.deleteSubscriber(id)
                                        },
                                        onOpenSubscriberDetail = foundationViewModel::loadSubscriberHistory,
                                        onCloseSubscriberDetail = foundationViewModel::clearSubscriberHistory,
                                        onPrepareTariffPreview = foundationViewModel::prepareTariffPreview,
                                        onClearTariffPreview = foundationViewModel::clearTariffPreview,
                                        onApplyTariffUpdate = foundationViewModel::applyTariffUpdate,
                                        notificationPrefs = notificationPrefs
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_READINGS) {
                                    MeterReadingsScreen(
                                        subscribers = uiState.subscribers,
                                        readings = meterReadings,
                                        invoices = invoices,
                                        feedback = billingFeedback,
                                        onDismissFeedback = billingViewModel::clearFeedback,
                                        onSaveReading = billingViewModel::saveMeterReading,
                                        onUpdateReading = billingViewModel::updateMeterReading,
                                        onDeleteReading = billingViewModel::deleteMeterReading,
                                        onIssueInvoiceForReading = { readingId ->
                                            billingViewModel.issueInvoiceForExistingReading(readingId)
                                        },
                                        notificationPrefs = notificationPrefs,
                                        lastIssuedInvoice = lastIssuedInvoice,
                                        onClearLastIssuedInvoice = billingViewModel::clearLastIssuedInvoice,
                                        onFeedbackWarning = billingViewModel::showWarningFeedback
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_INVOICES) {
                                    InvoicesManagementScreen(
                                        invoices = invoices,
                                        subscribers = uiState.subscribers,
                                        selectedInvoiceDetail = selectedInvoiceDetail,
                                        feedback = billingFeedback,
                                        onDismissFeedback = billingViewModel::clearFeedback,
                                        onSelectInvoice = billingViewModel::loadInvoiceDetail,
                                        onDismissDetail = billingViewModel::clearInvoiceDetail,
                                        onRecordPayment = { invoiceId, amt, method, collectorId, done ->
                                            billingViewModel.recordPaymentOnInvoice(
                                                invoiceId = invoiceId,
                                                amountPaid = amt,
                                                paymentMethod = method,
                                                collectorUserId = collectorId,
                                                onDone = done
                                            )
                                        },
                                        onRepriceInvoice = billingViewModel::repriceOldInvoice,
                                        onDeleteInvoice = { invoiceId, onDone ->
                                            billingViewModel.deleteUnpaidInvoice(invoiceId = invoiceId, onDone = onDone)
                                        },
                                        users = uiState.users,
                                        collectionsWithReceipts = collectionsWithReceipts,
                                        onSelectReceipt = billingViewModel::selectReceipt,
                                        notificationPrefs = notificationPrefs,
                                        onFeedbackWarning = billingViewModel::showWarningFeedback
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_SYSTEM) {
                                    SystemDiagnosticsScreen(
                                        state = uiState,
                                        themeMode = themeMode,
                                        feedback = foundationFeedback,
                                        onDismissFeedback = foundationViewModel::clearFeedback,
                                        onSelectThemeMode = foundationViewModel::setThemeMode,
                                        onVerifySchema = foundationViewModel::verifyDatabaseIntegrity,
                                        onRequestSafBackup = {
                                            safExportLauncher.launch("alasal2027_backup.json")
                                        },
                                        notificationPrefs = notificationPrefs,
                                        onUpdateNotificationPrefs = foundationViewModel::updateNotificationPrefs,
                                        backupSessionPrefs = backupSessionPrefs,
                                        onSetAppLockEnabled = foundationViewModel::setAppLockEnabled,
                                        onSetInactivityTimeoutSeconds = foundationViewModel::setInactivityLockTimeoutSeconds,
                                        onLockAppNow = foundationViewModel::lockAppImmediately,
                                        onNavigateToCollections = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_COLLECTIONS)
                                        },
                                        onNavigateToReports = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_REPORTS)
                                        },
                                        onNavigateToReminders = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_REMINDERS)
                                        },
                                        onNavigateToAccounts = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_ACCOUNTS)
                                        },
                                        onNavigateToSyncBackup = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_SYNC_BACKUP)
                                        },
                                        onNavigateToTotalConsumption = {
                                            navigateToSecondaryRoute(AlasalRoutes.ROUTE_TOTAL_CONSUMPTION)
                                        }
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_COLLECTIONS) {
                                    CollectionsAndReceiptsScreen(
                                        invoices = invoices,
                                        subscribers = uiState.subscribers,
                                        users = uiState.users,
                                        collectionsWithReceipts = collectionsWithReceipts,
                                        selectedReceipt = selectedReceipt,
                                        notificationPrefs = notificationPrefs,
                                        feedback = billingFeedback,
                                        onDismissFeedback = billingViewModel::clearFeedback,
                                        onNavigateBack = { navController.popBackStack() },
                                        onRecordCollection = { invId, amt, method, notes, collectorId, done ->
                                            billingViewModel.recordPaymentOnInvoice(
                                                invoiceId = invId,
                                                amountPaid = amt,
                                                paymentMethod = method,
                                                notes = notes,
                                                collectorUserId = collectorId,
                                                openReceiptOnSuccess = true,
                                                onDone = done
                                            )
                                        },
                                        onSelectReceipt = billingViewModel::selectReceipt,
                                        onDismissReceipt = billingViewModel::clearSelectedReceipt
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_REPORTS) {
                                    ReportsAndStatementsScreen(
                                        subscribers = uiState.subscribers,
                                        invoices = invoices,
                                        collectionsWithReceipts = collectionsWithReceipts,
                                        readings = meterReadings,
                                        arrearsRecords = arrearsRecords,
                                        creditTransactions = creditTransactions,
                                        users = uiState.users,
                                        notificationPrefs = notificationPrefs,
                                        onNavigateBack = { navController.popBackStack() },
                                        initialTabIndex = 0
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_FIELD_COLLECTION) {
                                    ReportsAndStatementsScreen(
                                        subscribers = uiState.subscribers,
                                        invoices = invoices,
                                        collectionsWithReceipts = collectionsWithReceipts,
                                        readings = meterReadings,
                                        arrearsRecords = arrearsRecords,
                                        creditTransactions = creditTransactions,
                                        users = uiState.users,
                                        notificationPrefs = notificationPrefs,
                                        onNavigateBack = { navController.popBackStack() },
                                        initialTabIndex = 1
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_REMINDERS) {
                                    RemindersAndNotificationsScreen(
                                        subscribers = uiState.subscribers,
                                        reminders = reminders,
                                        notificationPrefs = notificationPrefs,
                                        feedback = foundationFeedback,
                                        onDismissFeedback = foundationViewModel::clearFeedback,
                                        onNavigateBack = { navController.popBackStack() },
                                        onUpdateNotificationPrefs = foundationViewModel::updateNotificationPrefs,
                                        onScheduleReminder = { ctx, subId, title, msg, notes, at, done ->
                                            foundationViewModel.scheduleReadingReminder(
                                                context = ctx,
                                                subscriberId = subId,
                                                title = title,
                                                message = msg,
                                                notes = notes,
                                                scheduledAt = at,
                                                onDone = done
                                            )
                                        },
                                        onCompleteReminder = foundationViewModel::completeReminder,
                                        onSnoozeReminder = foundationViewModel::snoozeReminder,
                                        onSendTestNotification = foundationViewModel::sendTestNotification
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_ACCOUNTS) {
                                    AccountsAndPermissionsScreen(
                                        users = uiState.users,
                                        rolePermissions = uiState.permissions,
                                        activeUser = activeUser,
                                        effectivePermissions = effectivePermissions,
                                        feedback = foundationFeedback,
                                        onDismissFeedback = foundationViewModel::clearFeedback,
                                        onNavigateBack = { navController.popBackStack() },
                                        onLoginWithCredentials = { username, password, onDone ->
                                            foundationViewModel.loginWithCredentials(
                                                username = username,
                                                password = password,
                                                onSuccess = { onDone() }
                                            )
                                        },
                                        onCreateUser = foundationViewModel::createUserAccount,
                                        onUpdateUser = foundationViewModel::updateUserAccount,
                                        onChangePassword = foundationViewModel::changeUserPassword,
                                        onToggleUserActive = foundationViewModel::toggleUserActive,
                                        onToggleUserBiometric = foundationViewModel::toggleUserBiometric
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_SYNC_BACKUP) {
                                    WifiSyncAndBackupScreen(
                                        effectivePermissions = effectivePermissions,
                                        syncConnectionState = syncConnectionState,
                                        syncStatusMessage = syncStatusMessage,
                                        syncLocalIp = syncLocalIp,
                                        syncHostingPort = syncHostingPort,
                                        discoveredPeers = discoveredSyncPeers,
                                        syncLogs = syncLogs,
                                        lastSyncSummary = lastSyncSummary,
                                        backups = backupsList,
                                        backupSessionPrefs = backupSessionPrefs,
                                        lastRestoreResult = lastRestoreResult,
                                        backupAndRestoreManager = backupAndRestoreManager,
                                        feedback = foundationFeedback,
                                        onDismissFeedback = foundationViewModel::clearFeedback,
                                        onNavigateBack = {
                                            if (!navController.popBackStack()) {
                                                navigateToRoute(AlasalRoutes.ROUTE_DASHBOARD)
                                            }
                                        },
                                        onStartHosting = { foundationViewModel.startWifiSyncHosting() },
                                        onStopHosting = foundationViewModel::stopWifiSyncHosting,
                                        onStartDiscovery = foundationViewModel::startWifiPeerDiscovery,
                                        onSyncWithPeer = foundationViewModel::syncWithWifiPeer,
                                        onRetrySync = foundationViewModel::retryLastFailedWifiSync,
                                        onCreateManualBackup = foundationViewModel::createManualBackupPackage,
                                        onVerifyBackup = foundationViewModel::verifyBackupPackage,
                                        onExportBackupToUri = { uri, file ->
                                            foundationViewModel.exportFullBackupToUri(uri, file)
                                        },
                                        onDeleteBackup = foundationViewModel::deleteBackupFile,
                                        onToggleDailyAutoBackup = { enabled ->
                                            foundationViewModel.toggleDailyAutoBackup(context, enabled)
                                        },
                                        onTriggerAutoBackupNow = {
                                            foundationViewModel.triggerImmediateDailyAutoBackup(context)
                                        },
                                        onSaveCustomBackupFolder = foundationViewModel::saveCustomBackupFolder,
                                        onExecuteSafeRestoreFromFile = { file ->
                                            foundationViewModel.executeSafeRestoreFromFile(file)
                                        },
                                        onExecuteSafeRestoreFromUri = { uri ->
                                            foundationViewModel.executeSafeRestoreFromUri(uri)
                                        },
                                        initialTab = if (openBackupReportOnStart) 1 else 0,
                                        onOpenDashboard = { navigateToRoute(AlasalRoutes.ROUTE_DASHBOARD) }
                                    )
                                }

                                composable(AlasalRoutes.ROUTE_TOTAL_CONSUMPTION) {
                                    TotalConsumptionScreen(
                                        subscribers = uiState.subscribers,
                                        readings = meterReadings,
                                        invoices = invoices,
                                        notificationPrefs = notificationPrefs,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }

                            if (selectedReceipt != null && currentDestination?.route != AlasalRoutes.ROUTE_COLLECTIONS) {
                                ReceiptDetailDialog(
                                    receipt = selectedReceipt,
                                    subscriberPhone = subMap[selectedReceipt.subscriberId]?.phoneNumber,
                                    prefs = notificationPrefs,
                                    onDismiss = billingViewModel::clearSelectedReceipt
                                )
                            }
                        }
                    }
                }
            }
        }

        val readyState = uiState as? FoundationUiState.Ready
        if (isAppLocked && readyState != null) {
            BackHandler(enabled = true) { }
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                AppLockScreen(
                    users = readyState.users,
                    activeUser = activeUser,
                    onPasswordUnlock = { username, password, onError ->
                        foundationViewModel.loginWithCredentials(
                            username = username,
                            password = password,
                            onError = onError
                        )
                    },
                    onBiometricSuccess = { userId, onError ->
                        foundationViewModel.unlockWithBiometric(
                            userId = userId,
                            onError = onError
                        )
                    }
                )
            }
        }
    }
}
