package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.service.AppThemeMode
import com.example.service.BackupAndSessionPrefs
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import com.example.ui.viewmodel.FoundationUiState

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
    modifier: Modifier = Modifier
) {
    val alasalColors = LocalAlasalColors.current
    var stationPhoneInput by remember(notificationPrefs.stationPhone) { mutableStateOf(notificationPrefs.stationPhone) }
    var stationAddressInput by remember(notificationPrefs.stationAddress) { mutableStateOf(notificationPrefs.stationAddress) }
    var countryCodeInput by remember(notificationPrefs.defaultCountryCode) { mutableStateOf(notificationPrefs.defaultCountryCode) }

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

                            Text(
                                text = "إعدادات الطباعة الحرارية وبيانات المؤسسة على الإيصال",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = notificationPrefs.thermalPaperWidthMm == 58,
                                    onClick = { onUpdateNotificationPrefs { it.copy(thermalPaperWidthMm = 58) } },
                                    label = { Text("ورق حراري 58mm") }
                                )
                                FilterChip(
                                    selected = notificationPrefs.thermalPaperWidthMm == 80,
                                    onClick = { onUpdateNotificationPrefs { it.copy(thermalPaperWidthMm = 80) } },
                                    label = { Text("ورق حراري 80mm") }
                                )
                            }
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
                                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("whatsapp_country_code_save_button")
                            ) {
                                Text("حفظ بيانات المؤسسة ورمز الدولة لواتساب")
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
}
