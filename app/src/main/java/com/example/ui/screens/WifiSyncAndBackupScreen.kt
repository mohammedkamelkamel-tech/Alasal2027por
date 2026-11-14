package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.domain.auth.StationPermission
import com.example.domain.sync.SyncConnectionState
import com.example.domain.sync.SyncLogEntry
import com.example.domain.sync.SyncMergeSummary
import com.example.domain.sync.SyncPeerInfo
import com.example.service.BackupAndRestoreManager
import com.example.service.BackupAndSessionPrefs
import com.example.service.BackupRecordInfo
import com.example.service.LocalWifiSyncEngine
import com.example.service.RestoreExecutionResult
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WifiSyncAndBackupScreen(
    effectivePermissions: Set<String>,
    syncConnectionState: SyncConnectionState,
    syncStatusMessage: String,
    syncLocalIp: String,
    syncHostingPort: Int?,
    discoveredPeers: List<SyncPeerInfo>,
    syncLogs: List<SyncLogEntry>,
    lastSyncSummary: SyncMergeSummary?,
    backups: List<BackupRecordInfo>,
    backupSessionPrefs: BackupAndSessionPrefs,
    lastRestoreResult: RestoreExecutionResult?,
    backupAndRestoreManager: BackupAndRestoreManager?,
    feedback: FeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onNavigateBack: () -> Unit,
    onStartHosting: () -> Unit,
    onStopHosting: () -> Unit,
    onStartDiscovery: () -> Unit,
    onSyncWithPeer: (String, Int) -> Unit,
    onRetrySync: () -> Unit,
    onCreateManualBackup: () -> Unit,
    onVerifyBackup: (File) -> Unit,
    onExportBackupToUri: (Uri, File?) -> Unit,
    onDeleteBackup: (File) -> Unit,
    onToggleDailyAutoBackup: (Boolean) -> Unit,
    onTriggerAutoBackupNow: () -> Unit,
    onSaveCustomBackupFolder: (Uri) -> Unit,
    onExecuteSafeRestoreFromFile: (File) -> Unit,
    onExecuteSafeRestoreFromUri: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }

    val canManageSync = StationPermission.MANAGE_SYNC.key in effectivePermissions
    val canManageBackups = StationPermission.EXPORT_BACKUP.key in effectivePermissions

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Wi-Fi Sync, 1 = Backup & Restore
    var targetIpInput by remember { mutableStateOf("192.168.1.") }
    var targetPortInput by remember { mutableStateOf(LocalWifiSyncEngine.DEFAULT_PORT.toString()) }

    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var pendingExportSource by remember { mutableStateOf<File?>(null) }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            onExportBackupToUri(uri, pendingExportSource)
        }
        pendingExportSource = null
    }

    val openRestoreDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onExecuteSafeRestoreFromUri(uri)
        }
    }

    val openCustomFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            onSaveCustomBackupFolder(treeUri)
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
                    .widthIn(max = 820.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("wifi_sync_backup_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        Text(
                            text = "المزامنة والنسخ الاحتياطي والاستعادة",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("المزامنة عبر Wi-Fi") },
                            modifier = Modifier.weight(1f).testTag("tab_wifi_sync")
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            leadingIcon = { Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("النسخ والاستعادة الآمنة") },
                            modifier = Modifier.weight(1f).testTag("tab_backup_restore")
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                item(key = "wifi_host_card") {
                    Card(
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("حالة المزامنة المحلية (بدون خادم)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = when (syncConnectionState) {
                                        SyncConnectionState.SUCCEEDED, SyncConnectionState.HOSTING -> alasalColors.creditContainer
                                        SyncConnectionState.FAILED -> alasalColors.arrearsContainer
                                        else -> alasalColors.gold.copy(alpha = 0.16f)
                                    }
                                ) {
                                    Text(
                                        text = when (syncConnectionState) {
                                            SyncConnectionState.IDLE -> "جاهز"
                                            SyncConnectionState.HOSTING -> "بانتظار جهاز"
                                            SyncConnectionState.DISCOVERING -> "بحث..."
                                            SyncConnectionState.SYNCING -> "تزامن..."
                                            SyncConnectionState.SUCCEEDED -> "مكتمل"
                                            SyncConnectionState.FAILED -> "فشل"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(syncStatusMessage, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "عنوان هذا الجهاز على Wi-Fi: $syncLocalIp | المنفذ: ${syncHostingPort ?: LocalWifiSyncEngine.DEFAULT_PORT}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { if (syncHostingPort == null) onStartHosting() else onStopHosting() },
                                    enabled = canManageSync,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_wifi_hosting_button")
                                ) {
                                    Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(if (syncHostingPort == null) "تفعيل الاستقبال" else "إيقاف الاستقبال")
                                }
                                OutlinedButton(
                                    onClick = onStartDiscovery,
                                    enabled = canManageSync,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("discover_wifi_peers_button")
                                ) {
                                    Icon(Icons.Filled.WifiFind, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("بحث تلقائي")
                                }
                            }

                            if (lastSyncSummary != null) {
                                Surface(
                                    color = alasalColors.creditContainer.copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "آخر دمج: إضافة ${lastSyncSummary.insertedCount} | تحديث ${lastSyncSummary.updatedCount} | تعارضات محلولة ${lastSyncSummary.conflictsResolvedCount} | عمليات مالية محفوظة ${lastSyncSummary.financialOpsPreservedCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "wifi_client_connect") {
                    Card(
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("الاتصال بجهاز آخر على نفس شبكة Wi-Fi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = targetIpInput,
                                    onValueChange = { targetIpInput = it },
                                    label = { Text("عنوان IP") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f).testTag("wifi_target_ip_input")
                                )
                                OutlinedTextField(
                                    value = targetPortInput,
                                    onValueChange = { targetPortInput = it },
                                    label = { Text("المنفذ") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("wifi_target_port_input")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val port = targetPortInput.toIntOrNull() ?: LocalWifiSyncEngine.DEFAULT_PORT
                                        onSyncWithPeer(targetIpInput, port)
                                    },
                                    enabled = canManageSync,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("start_wifi_sync_button")
                                ) {
                                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("بدء المزامنة الآن")
                                }
                                if (syncConnectionState == SyncConnectionState.FAILED) {
                                    OutlinedButton(
                                        onClick = onRetrySync,
                                        modifier = Modifier.height(48.dp).testTag("retry_wifi_sync_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("إعادة المحاولة")
                                    }
                                }
                            }

                            if (discoveredPeers.isNotEmpty()) {
                                HorizontalDivider()
                                Text("أجهزة محطة العسل المكتشفة:", style = MaterialTheme.typography.labelLarge)
                                discoveredPeers.forEach { peer ->
                                    OutlinedButton(
                                        onClick = {
                                            targetIpInput = peer.hostAddress
                                            targetPortInput = peer.port.toString()
                                            onSyncWithPeer(peer.hostAddress, peer.port)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Text("مزامنة مع ${peer.deviceName} (${peer.hostAddress}:${peer.port})")
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "sync_logs_header") {
                    Text(
                        text = "سجل المزامنة والأخطاء (${syncLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()
                    )
                }

                if (syncLogs.isEmpty()) {
                    item(key = "sync_logs_empty") {
                        EmptyStateCard(
                            icon = Icons.Filled.Wifi,
                            title = "لا توجد عمليات مزامنة مسجلة بعد",
                            description = "فعّل الاستقبال على أحد الجهازين ثم اتصل من الجهاز الآخر لمزامنة المشتركين والفواتير والتحصيلات والحسابات دون تكرار أو فقدان.",
                            modifier = Modifier.widthIn(max = 820.dp)
                        )
                    }
                } else {
                    items(syncLogs, key = { it.id }) { log ->
                        Card(
                            modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (log.isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (log.isSuccess) alasalColors.creditGreen else alasalColors.arrearsRed
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${log.message} (${log.peerLabel})", style = MaterialTheme.typography.titleSmall)
                                    if (log.details.isNotBlank()) {
                                        Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(dateFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Backup & Safe 18-Step Restore
                item(key = "backup_controls") {
                    Card(
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("إدارة النسخ الاحتياطي التلقائي واليدوي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("نسخ احتياطي يومي تلقائي", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    val lastAutoText = if (backupSessionPrefs.lastAutoBackupAt > 0L) {
                                        "آخر نسخة تلقائية: ${dateFormat.format(Date(backupSessionPrefs.lastAutoBackupAt))}"
                                    } else {
                                        "مجدول كل 24 ساعة عبر WorkManager"
                                    }
                                    Text(lastAutoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = backupSessionPrefs.dailyAutoBackupEnabled,
                                    onCheckedChange = onToggleDailyAutoBackup,
                                    enabled = canManageBackups,
                                    modifier = Modifier.testTag("daily_auto_backup_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onCreateManualBackup,
                                    enabled = canManageBackups,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("create_manual_backup_button")
                                ) {
                                    Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("نسخة يدوية الآن")
                                }
                                OutlinedButton(
                                    onClick = onTriggerAutoBackupNow,
                                    enabled = canManageBackups,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("trigger_auto_backup_button")
                                ) {
                                    Text("تشغيل التلقائي الآن")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { openCustomFolderLauncher.launch(null) },
                                    enabled = canManageBackups,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("choose_custom_backup_folder_button")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = if (backupSessionPrefs.customBackupFolderUri != null) "مجلد خارجي مفعّل" else "مجلد / SD Card",
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingExportSource = null
                                        exportDocLauncher.launch("alasal2027_full_backup.alasalbak")
                                    },
                                    enabled = canManageBackups,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("export_saf_full_backup_button")
                                ) {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("حفظ خارجي (SAF)", maxLines = 1)
                                }
                            }

                            OutlinedButton(
                                onClick = { openRestoreDocLauncher.launch(arrayOf("*/*")) },
                                enabled = canManageBackups,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("restore_external_file_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = alasalColors.arrearsRed)
                            ) {
                                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("اختيار واستعادة نسخة من الجهاز أو Google Drive (18 خطوة آمنة)")
                            }
                        }
                    }
                }

                if (lastRestoreResult != null) {
                    item(key = "last_restore_steps") {
                        Card(
                            modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth().testTag("restore_steps_card"),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (lastRestoreResult.succeeded) {
                                    alasalColors.creditContainer.copy(alpha = 0.35f)
                                } else {
                                    alasalColors.arrearsContainer.copy(alpha = 0.4f)
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (lastRestoreResult.succeeded) alasalColors.creditGreen else alasalColors.arrearsRed
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "تقرير الاستعادة الآمنة (18 خطوة)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = lastRestoreResult.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lastRestoreResult.succeeded) alasalColors.creditGreen else alasalColors.arrearsRed
                                )
                                lastRestoreResult.steps.forEach { st ->
                                    Text(
                                        text = "${st.stepNumber}. ${if (st.succeeded) "✓" else "✕"} ${st.title} — ${st.details}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "backups_list_header") {
                    Text(
                        text = "سجل النسخ الاحتياطية المحفوظة (${backups.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()
                    )
                }

                if (backups.isEmpty()) {
                    item(key = "backups_empty") {
                        EmptyStateCard(
                            icon = Icons.Filled.Backup,
                            title = "لا توجد نسخ احتياطية كاملة بعد",
                            description = "اضغط «نسخة يدوية الآن» لإنشاء حزمة محطة العسل الكاملة الموثقة بالبصمة مع التاريخ والحجم.",
                            modifier = Modifier.widthIn(max = 820.dp)
                        )
                    }
                } else {
                    items(backups, key = { it.id }) { rec ->
                        val recFile = remember(rec.absolutePath) { File(rec.absolutePath) }
                        val sizeLabel = remember(rec.fileSizeBytes) {
                            backupAndRestoreManager?.formatBytes(rec.fileSizeBytes) ?: "${rec.fileSizeBytes} بايت"
                        }
                        Card(
                            modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth().testTag("backup_card_${rec.fileName}"),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                            border = BorderStroke(1.dp, alasalColors.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rec.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "التاريخ: ${dateFormat.format(Date(rec.createdAt))} | الحجم: $sizeLabel | الإصدار: v${rec.dbVersion}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "المحتوى: ${rec.subscribersCount} مشترك، ${rec.invoicesCount} فاتورة، ${rec.collectionsCount} تحصيل",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (rec.isVerified) {
                                        Icon(Icons.Filled.Verified, contentDescription = "موثقة", tint = alasalColors.creditGreen)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onVerifyBackup(recFile) },
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("verify_backup_${rec.fileName}")
                                    ) {
                                        Text("فحص", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            backupAndRestoreManager?.let { mgr ->
                                                val intent = mgr.createShareOrDriveIntent(recFile, targetGoogleDrive = false)
                                                runCatching { context.startActivity(intent) }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("share_backup_${rec.fileName}")
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("مشاركة/Drive", maxLines = 1)
                                    }
                                    Button(
                                        onClick = { pendingRestoreFile = recFile },
                                        enabled = canManageBackups,
                                        modifier = Modifier.weight(1f).height(44.dp).testTag("restore_backup_${rec.fileName}")
                                    ) {
                                        Text("استعادة", maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = { onDeleteBackup(recFile) },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف النسخة", tint = alasalColors.arrearsRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingRestoreFile != null) {
        val target = pendingRestoreFile!!
        AlertDialog(
            onDismissRequest = { pendingRestoreFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 460.dp),
            title = { Text("تأكيد الاستعادة الآمنة (18 خطوة)") },
            text = {
                Text(
                    text = "سيقوم النظام بفحص الحزمة (${target.name}) في بيئة معزولة، إنشاء نسخة أمان للوضع الحالي، تفريغ سجل WAL وإغلاق قاعدة البيانات، ثم الاستبدال الذري والتحقق من سلامة الجداول الـ14. لن يتم حذف بياناتك الحالية عند أي فشل.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreFile = null
                        onExecuteSafeRestoreFromFile(target)
                    },
                    modifier = Modifier.testTag("confirm_safe_restore_button")
                ) { Text("بدء الاستعادة الآمنة") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreFile = null }) { Text("إلغاء") }
            }
        )
    }
}
