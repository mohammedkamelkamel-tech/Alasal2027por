package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.service.AppThemeMode
import com.example.service.BackupAndSessionPrefs
import com.example.service.BluetoothThermalPrinterService
import com.example.service.NotificationAndPrintPrefs
import com.example.service.PairedBluetoothPrinter
import com.example.service.PrinterConnectionState
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import com.example.ui.viewmodel.FoundationUiState
import kotlinx.coroutines.launch

@Composable
fun SystemDiagnosticsScreen(
    state: FoundationUiState.Ready,
    themeMode: AppThemeMode,
    feedback: FeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onVerifySchema: () -> Unit,
    onRequestSafBackup: () -> Unit,
    notificationPrefs: NotificationAndPrintPrefs = NotificationAndPrintPrefs(),
    onUpdateNotificationPrefs: ((NotificationAndPrintPrefs) -> NotificationAndPrintPrefs) -> Unit = {},
    backupSessionPrefs: BackupAndSessionPrefs = BackupAndSessionPrefs(),
    onSetAppLockEnabled: (Boolean) -> Unit = {},
    onSetInactivityTimeoutSeconds: (Int) -> Unit = {},
    onLockAppNow: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
    onNavigateToSyncBackup: () -> Unit = {},
    onNavigateToTotalConsumption: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alasalColors = LocalAlasalColors.current
    var stationPhoneInput by remember(notificationPrefs.stationPhone) { mutableStateOf(notificationPrefs.stationPhone) }
    var stationAddressInput by remember(notificationPrefs.stationAddress) { mutableStateOf(notificationPrefs.stationAddress) }
    var countryCodeInput by remember(notificationPrefs.defaultCountryCode) { mutableStateOf(notificationPrefs.defaultCountryCode) }

    var showBluetoothPickerDialog by remember { mutableStateOf(false) }
    var pairedPrinters by remember { mutableStateOf<List<PairedBluetoothPrinter>>(emptyList()) }
    var printerConnectionState by remember { mutableStateOf(PrinterConnectionState.NOT_CONFIGURED) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var isPrintingTest by remember { mutableStateOf(false) }
    var printerActionMessage by remember { mutableStateOf<String?>(null) }
    var printerActionIsError by remember { mutableStateOf(false) }
    var pendingActionAfterPermission by remember { mutableStateOf<String?>(null) }

    fun loadPairedPrintersAndOpenDialog() {
        val result = BluetoothThermalPrinterService.getPairedPrinters(context)
        result.onSuccess { list ->
            pairedPrinters = list
            showBluetoothPickerDialog = true
            printerActionMessage = if (list.isEmpty()) {
                "لا توجد أجهزة بلوتوث مقترنة بالهاتف حالياً. قم بإقران الطابعة من إعدادات البلوتوث أولاً."
            } else {
                null
            }
            printerActionIsError = list.isEmpty()
        }.onFailure { err ->
            printerActionMessage = err.message ?: "تعذر جلب قائمة أجهزة البلوتوث المقترنة."
            printerActionIsError = true
        }
    }

    fun refreshConnectionStatus(mac: String = notificationPrefs.printerDeviceAddress) {
        if (mac.isBlank() || notificationPrefs.printerConnectionType != "BLUETOOTH") {
            printerConnectionState = PrinterConnectionState.NOT_CONFIGURED
            return
        }
        scope.launch {
            isCheckingConnection = true
            printerConnectionState = BluetoothThermalPrinterService.checkPrinterConnection(context, mac)
            isCheckingConnection = false
        }
    }

    fun executeTestPrint() {
        if (!BluetoothThermalPrinterService.isBluetoothPrinterConfigured(notificationPrefs)) {
            printerActionIsError = true
            printerActionMessage = "يرجى اختيار طابعة بلوتوث أولاً قبل إجراء الطباعة التجريبية."
            return
        }
        scope.launch {
            isPrintingTest = true
            printerActionMessage = "جاري إرسال صفحة الاختبار إلى الطابعة الحرارية..."
            printerActionIsError = false
            val res = BluetoothThermalPrinterService.printTestPage(context, notificationPrefs)
            res.onSuccess { msg ->
                printerConnectionState = PrinterConnectionState.CONNECTED
                printerActionIsError = false
                printerActionMessage = msg
            }.onFailure { err ->
                printerConnectionState = PrinterConnectionState.DISCONNECTED
                printerActionIsError = true
                printerActionMessage = err.message ?: "فشلت الطباعة التجريبية."
            }
            isPrintingTest = false
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            when (pendingActionAfterPermission) {
                "PICK" -> loadPairedPrintersAndOpenDialog()
                "TEST_PRINT" -> executeTestPrint()
                "CHECK" -> refreshConnectionStatus()
            }
        } else {
            printerActionIsError = true
            printerActionMessage = "لم يتم منح أذونات البلوتوث (BLUETOOTH_CONNECT / BLUETOOTH_SCAN)."
            printerConnectionState = PrinterConnectionState.PERMISSION_REQUIRED
        }
        pendingActionAfterPermission = null
    }

    fun runWithBluetoothPermission(actionKey: String, onGranted: () -> Unit) {
        if (BluetoothThermalPrinterService.hasBluetoothPermissions(context) ||
            BluetoothThermalPrinterService.pairedPrintersProviderForTest != null
        ) {
            onGranted()
        } else {
            val required = BluetoothThermalPrinterService.requiredBluetoothPermissions()
            if (required.isEmpty()) {
                onGranted()
            } else {
                pendingActionAfterPermission = actionKey
                bluetoothPermissionLauncher.launch(required)
            }
        }
    }

    LaunchedEffect(notificationPrefs.printerConnectionType, notificationPrefs.printerDeviceAddress) {
        if (BluetoothThermalPrinterService.isBluetoothPrinterConfigured(notificationPrefs)) {
            if (BluetoothThermalPrinterService.hasBluetoothPermissions(context) ||
                BluetoothThermalPrinterService.connectionCheckerForTest != null
            ) {
                printerConnectionState = BluetoothThermalPrinterService.checkPrinterConnection(
                    context,
                    notificationPrefs.printerDeviceAddress
                )
            } else {
                printerConnectionState = PrinterConnectionState.PERMISSION_REQUIRED
            }
        } else {
            printerConnectionState = PrinterConnectionState.NOT_CONFIGURED
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (feedback != null) {
            StatusFeedbackBanner(
                feedback = feedback,
                onDismiss = onDismissFeedback,
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("system_diagnostics_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "theme_and_actions") {
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "إدارة النظام والحسابات والمزامنة والنسخ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onNavigateToAccounts,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("system_accounts_button")
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("الحسابات والصلاحيات", maxLines = 1)
                        }
                        Button(
                            onClick = onNavigateToSyncBackup,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("system_sync_backup_button")
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("المزامنة والنسخ الآمن", maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToTotalConsumption,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("system_total_consumption_button")
                    ) {
                        Text(text = stringResource(R.string.title_total_consumption) + " (الشهري ونصف الشهري)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToCollections,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("التحصيل", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onNavigateToReports,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("التقارير", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onNavigateToReminders,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("التذكيرات", maxLines = 1)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "مظهر التطبيق (الوضع النهاري والليلي)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = themeMode == AppThemeMode.SYSTEM,
                                    onClick = { onSelectThemeMode(AppThemeMode.SYSTEM) },
                                    label = { Text("تلقائي") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("theme_chip_system")
                                )
                                FilterChip(
                                    selected = themeMode == AppThemeMode.LIGHT,
                                    onClick = { onSelectThemeMode(AppThemeMode.LIGHT) },
                                    label = { Text("نهاري") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("theme_chip_light")
                                )
                                FilterChip(
                                    selected = themeMode == AppThemeMode.DARK,
                                    onClick = { onSelectThemeMode(AppThemeMode.DARK) },
                                    label = { Text("ليلي") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.testTag("theme_chip_dark")
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bluetooth_printer_section") {
                Card(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .testTag("bluetooth_printer_settings_card"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.2.dp, alasalColors.gold.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (printerConnectionState == PrinterConnectionState.CONNECTED) {
                                        Icons.Filled.BluetoothConnected
                                    } else {
                                        Icons.Filled.Bluetooth
                                    },
                                    contentDescription = null,
                                    tint = if (printerConnectionState == PrinterConnectionState.CONNECTED) {
                                        alasalColors.creditGreen
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                                Text(
                                    text = "إعدادات الطابعة الحرارية (Bluetooth Printer)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val isConnected = printerConnectionState == PrinterConnectionState.CONNECTED
                            val statusLabel = when (printerConnectionState) {
                                PrinterConnectionState.CONNECTED -> "متصل"
                                PrinterConnectionState.DISCONNECTED -> "غير متصل"
                                PrinterConnectionState.BLUETOOTH_DISABLED -> "البلوتوث مغلق"
                                PrinterConnectionState.PERMISSION_REQUIRED -> "يلزم الإذن"
                                PrinterConnectionState.NOT_CONFIGURED -> "غير متصل"
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (isConnected) alasalColors.creditContainer else alasalColors.arrearsContainer,
                                modifier = Modifier.testTag("bluetooth_printer_status_badge")
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) alasalColors.creditGreen else alasalColors.arrearsRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "اختر طابعة البلوتوث المقترنة لتفعيل الطباعة الحرارية الفورية المباشرة (ESC/POS) للفواتير والإيصالات وكشف الحساب بدون نافذة معاينة أندرويد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = notificationPrefs.thermalPaperWidthMm == 58,
                                onClick = { onUpdateNotificationPrefs { it.copy(thermalPaperWidthMm = 58) } },
                                label = { Text("ورق حراري 58mm") },
                                modifier = Modifier.testTag("printer_width_58_chip")
                            )
                            FilterChip(
                                selected = notificationPrefs.thermalPaperWidthMm != 58,
                                onClick = { onUpdateNotificationPrefs { it.copy(thermalPaperWidthMm = 80) } },
                                label = { Text("ورق حراري 80mm") },
                                modifier = Modifier.testTag("printer_width_80_chip")
                            )
                        }

                        if (BluetoothThermalPrinterService.isBluetoothPrinterConfigured(notificationPrefs)) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, alasalColors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("saved_bluetooth_printer_info")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = notificationPrefs.printerDeviceName.ifBlank { "طابعة بلوتوث افتراضية" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "MAC: ${notificationPrefs.printerDeviceAddress}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            runWithBluetoothPermission("CHECK") {
                                                refreshConnectionStatus()
                                            }
                                        },
                                        enabled = !isCheckingConnection,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("bluetooth_check_connection_button")
                                    ) {
                                        if (isCheckingConnection) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Filled.Refresh, contentDescription = "فحص اتصال الطابعة")
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "لا توجد طابعة بلوتوث افتراضية محددة بعد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    runWithBluetoothPermission("PICK") {
                                        loadPairedPrintersAndOpenDialog()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("select_bluetooth_printer_button")
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("اختيار طابعة بلوتوث", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    runWithBluetoothPermission("TEST_PRINT") {
                                        executeTestPrint()
                                    }
                                },
                                enabled = !isPrintingTest &&
                                    BluetoothThermalPrinterService.isBluetoothPrinterConfigured(notificationPrefs),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("bluetooth_test_print_button")
                            ) {
                                if (isPrintingTest) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("طباعة تجريبية", maxLines = 1)
                            }
                        }

                        if (BluetoothThermalPrinterService.isBluetoothPrinterConfigured(notificationPrefs)) {
                            TextButton(
                                onClick = {
                                    onUpdateNotificationPrefs {
                                        it.copy(
                                            printerConnectionType = "SYSTEM",
                                            printerDeviceName = "",
                                            printerDeviceAddress = ""
                                        )
                                    }
                                    printerConnectionState = PrinterConnectionState.NOT_CONFIGURED
                                    printerActionIsError = false
                                    printerActionMessage = "تم إلغاء تعيين طابعة البلوتوث الافتراضية."
                                },
                                modifier = Modifier.testTag("clear_bluetooth_printer_button")
                            ) {
                                Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("إلغاء ربط طابعة البلوتوث")
                            }
                        }

                        if (printerActionMessage != null) {
                            Text(
                                text = printerActionMessage!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (printerActionIsError) alasalColors.arrearsRed else alasalColors.creditGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("bluetooth_printer_feedback")
                            )
                        }
                    }
                }
            }

            item(key = "station_and_whatsapp_settings") {
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "بيانات المؤسسة وواتساب على الإيصال",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = stationPhoneInput,
                                onValueChange = { stationPhoneInput = it },
                                label = { Text("هاتف المؤسسة") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = stationAddressInput,
                                onValueChange = { stationAddressInput = it },
                                label = { Text("عنوان المؤسسة") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = countryCodeInput,
                                onValueChange = { countryCodeInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("رمز الدولة الافتراضي لواتساب (مثل 967 أو 966)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("whatsapp_country_code_input")
                            )
                            OutlinedButton(
                                onClick = {
                                    onUpdateNotificationPrefs {
                                        it.copy(
                                            stationPhone = stationPhoneInput.ifBlank { it.stationPhone },
                                            stationAddress = stationAddressInput.ifBlank { it.stationAddress },
                                            defaultCountryCode = countryCodeInput.filter { ch -> ch.isDigit() }.ifBlank { "967" }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("whatsapp_country_code_save_button")
                            ) {
                                Text("حفظ بيانات المؤسسة ورمز الدولة لواتساب")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("السماح بإرسال جميع الفواتير عبر SMS", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "يظهر زر الإرسال الجماعي في شاشة الفواتير عند التفعيل",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = notificationPrefs.bulkInvoiceSmsEnabled,
                                    onCheckedChange = { enabled ->
                                        onUpdateNotificationPrefs { it.copy(bulkInvoiceSmsEnabled = enabled) }
                                    },
                                    modifier = Modifier.testTag("bulk_invoice_sms_enabled_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onVerifySchema,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text(text = stringResource(R.string.action_verify_schema))
                                }
                                OutlinedButton(
                                    onClick = onRequestSafBackup,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(text = "تصدير SAF")
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = null, tint = alasalColors.gold)
                                Text(
                                    text = "الأمان والقفل التلقائي للتطبيق",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تفعيل القفل التلقائي (كلمة المرور / البصمة)", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "يقفل عند تشغيل التطبيق أو الخروج للخلفية أو انتهاء المهلة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = backupSessionPrefs.appLockEnabled,
                                    onCheckedChange = onSetAppLockEnabled,
                                    modifier = Modifier.testTag("app_lock_enabled_switch")
                                )
                            }
                            Text("مهلة القفل التلقائي عند عدم التفاعل:", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0 to "فوري",
                                    30 to "30ث",
                                    120 to "2د",
                                    300 to "5د"
                                ).forEach { (sec, label) ->
                                    FilterChip(
                                        selected = backupSessionPrefs.inactivityLockTimeoutSeconds == sec,
                                        onClick = { onSetInactivityTimeoutSeconds(sec) },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("timeout_chip_$sec")
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onLockAppNow,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("lock_app_now_button")
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("قفل التطبيق فوراً")
                            }
                        }
                    }
                }
            }

            item(key = "tables_header") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = alasalColors.gold
                    )
                    Text(
                        text = stringResource(R.string.section_database_status),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            items(state.tableStatuses, key = { it.tableName }) { table ->
                Card(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .testTag("table_status_${table.tableName}"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    border = BorderStroke(1.dp, alasalColors.cardBorder.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = alasalColors.creditGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "${table.arabicTitle} (${table.tableName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = table.relationshipNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            color = alasalColors.gold.copy(alpha = 0.18f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${table.rowCount} سجل",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            item(key = "users_header") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "المستخدمون والصلاحيات التأسيسية (${state.users.size} مستخدم، ${state.permissions.size} صلاحية)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            items(state.users, key = { it.id }) { user ->
                Card(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = user.fullName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "@${user.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = user.roleCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = alasalColors.gold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showBluetoothPickerDialog) {
        Dialog(
            onDismissRequest = { showBluetoothPickerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 560.dp)
                    .testTag("bluetooth_devices_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "أجهزة البلوتوث المقترنة",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "اختر الطابعة الحرارية الافتراضية من الأجهزة المقترنة بالهاتف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showBluetoothPickerDialog = false },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                        }
                    }

                    HorizontalDivider()

                    if (pairedPrinters.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا توجد أجهزة بلوتوث مقترنة بالهاتف. يرجى إقران الطابعة الحرارية أولاً من إعدادات البلوتوث في جهاز الأندرويد ثم إعادة المحاولة.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pairedPrinters, key = { it.macAddress }) { device ->
                                val isSelected = notificationPrefs.printerConnectionType == "BLUETOOTH" &&
                                    notificationPrefs.printerDeviceAddress.equals(device.macAddress, ignoreCase = true)
                                val selectedBlue = Color(0xFF1976D2)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bt_device_item_${device.macAddress}")
                                        .clickable {
                                            onUpdateNotificationPrefs {
                                                it.copy(
                                                    printerConnectionType = "BLUETOOTH",
                                                    printerDeviceName = device.name,
                                                    printerDeviceAddress = device.macAddress
                                                )
                                            }
                                            showBluetoothPickerDialog = false
                                            printerActionIsError = false
                                            printerActionMessage = "تم حفظ الطابعة الافتراضية: ${device.name} (${device.macAddress})"
                                            refreshConnectionStatus(device.macAddress)
                                        },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            selectedBlue.copy(alpha = 0.10f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) selectedBlue else alasalColors.cardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Bluetooth,
                                                contentDescription = null,
                                                tint = if (isSelected) selectedBlue else MaterialTheme.colorScheme.primary
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = device.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = device.macAddress,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "الطابعة المختارة",
                                                tint = selectedBlue,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Button(
                        onClick = { showBluetoothPickerDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("bluetooth_devices_close_button")
                    ) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }
}
