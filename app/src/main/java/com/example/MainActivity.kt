package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.AppThemeMode
import com.example.ui.navigation.AlasalAppShell
import com.example.ui.theme.Alasal2027Theme
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.FoundationViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AlasalApplication

        setContent {
            val databaseEpoch by app.databaseEpoch.collectAsStateWithLifecycle()

            key(databaseEpoch) {
                val foundationFactory = remember(databaseEpoch) {
                    FoundationViewModel.Factory(
                        repository = app.repository,
                        themePreferences = app.themePreferences,
                        safBackupManager = app.safBackupManager,
                        backupAndRestoreManager = app.backupAndRestoreManager,
                        localWifiSyncEngine = app.localWifiSyncEngine,
                        preserveUnlockedSession = databaseEpoch > 1
                    )
                }
                val billingFactory = remember(databaseEpoch) {
                    BillingViewModel.Factory(
                        billingRepository = app.billingRepository,
                        appContext = applicationContext,
                        themePreferences = app.themePreferences
                    )
                }

                val foundationViewModel: FoundationViewModel = viewModel(
                    key = "foundation_vm_$databaseEpoch",
                    factory = foundationFactory
                )
                val billingViewModel: BillingViewModel = viewModel(
                    key = "billing_vm_$databaseEpoch",
                    factory = billingFactory
                )

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, foundationViewModel) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> foundationViewModel.onAppWentToBackground()
                            Lifecycle.Event.ON_START -> foundationViewModel.onAppReturnedToForeground()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val uiState by foundationViewModel.uiState.collectAsStateWithLifecycle()
                val themeMode by foundationViewModel.themeMode.collectAsStateWithLifecycle()
                val notificationPrefs by foundationViewModel.notificationPrefs.collectAsStateWithLifecycle()
                val reminders by foundationViewModel.reminders.collectAsStateWithLifecycle()
                val foundationFeedback by foundationViewModel.feedback.collectAsStateWithLifecycle()
                val selectedHistory by foundationViewModel.selectedSubscriberHistory.collectAsStateWithLifecycle()
                val tariffPreview by foundationViewModel.tariffPreviewItems.collectAsStateWithLifecycle()

                val billingFeedback by billingViewModel.feedback.collectAsStateWithLifecycle()
                val meterReadings by billingViewModel.meterReadings.collectAsStateWithLifecycle()
                val invoices by billingViewModel.invoices.collectAsStateWithLifecycle()
                val collectionsWithReceipts by billingViewModel.collectionsWithReceipts.collectAsStateWithLifecycle()
                val arrearsRecords by billingViewModel.arrearsRecords.collectAsStateWithLifecycle()
                val creditTransactions by billingViewModel.creditTransactions.collectAsStateWithLifecycle()
                val selectedInvoiceDetail by billingViewModel.selectedInvoiceDetail.collectAsStateWithLifecycle()
                val selectedReceipt by billingViewModel.selectedReceipt.collectAsStateWithLifecycle()

                val systemDark = isSystemInDarkTheme()
                val isDarkTheme = when (themeMode) {
                    AppThemeMode.DARK -> true
                    AppThemeMode.LIGHT -> false
                    AppThemeMode.SYSTEM -> systemDark
                }

                Alasal2027Theme(darkTheme = isDarkTheme) {
                    AlasalAppShell(
                        uiState = uiState,
                        themeMode = themeMode,
                        notificationPrefs = notificationPrefs,
                        reminders = reminders,
                        isDarkTheme = isDarkTheme,
                        foundationFeedback = foundationFeedback,
                        billingFeedback = billingFeedback,
                        selectedSubscriberHistory = selectedHistory,
                        tariffPreviewItems = tariffPreview,
                        meterReadings = meterReadings,
                        invoices = invoices,
                        collectionsWithReceipts = collectionsWithReceipts,
                        arrearsRecords = arrearsRecords,
                        creditTransactions = creditTransactions,
                        selectedInvoiceDetail = selectedInvoiceDetail,
                        selectedReceipt = selectedReceipt,
                        foundationViewModel = foundationViewModel,
                        billingViewModel = billingViewModel,
                        backupAndRestoreManager = app.backupAndRestoreManager,
                        openBackupReportOnStart = databaseEpoch > 1 && app.backupAndRestoreManager.lastRestoreResult.value != null
                    )
                }
            }
        }
    }
}
