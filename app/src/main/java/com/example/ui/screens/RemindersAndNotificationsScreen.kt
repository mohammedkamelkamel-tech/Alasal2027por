package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.service.AlasalNotificationService
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.components.AvailableSubscribersDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.components.SubscriberPickerTriggerButton
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemindersAndNotificationsScreen(
    subscribers: List<SubscriberEntity>,
    reminders: List<ReminderEntity>,
    notificationPrefs: NotificationAndPrintPrefs,
    feedback: FeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onNavigateBack: () -> Unit,
    onUpdateNotificationPrefs: ((NotificationAndPrintPrefs) -> NotificationAndPrintPrefs) -> Unit,
    onScheduleReminder: (
        context: Context,
        subscriberId: Long?,
        title: String,
        message: String,
        notes: String,
        scheduledAt: Long,
        onDone: () -> Unit
    ) -> Unit,
    onCompleteReminder: (Long) -> Unit,
    onSnoozeReminder: (Context, Long, Long) -> Unit,
    onSendTestNotification: (Context) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }
    val subMap = remember(subscribers) { subscribers.associateBy { it.id } }

    var hasNotifPermission by remember {
        mutableStateOf(AlasalNotificationService.hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    var selectedSubscriberId by remember { mutableStateOf<Long?>(null) }
    var showSubscriberPickerDialog by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("موعد قراءة عداد") }
    var notesInput by remember { mutableStateOf("") }
    var delayMillis by remember { mutableLongStateOf(3_600_000L) }
    val baseNow = remember { System.currentTimeMillis() }
    val scheduledAt = baseNow + delayMillis

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
                .testTag("reminders_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Column {
                        Text("التذكيرات والإشعارات", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "مواعيد قراءة العدادات وتنبيهات الفواتير والتحصيلات (WorkManager)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "notification_settings") {
                Card(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, alasalColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("إعدادات الإشعارات والأصوات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                            OutlinedButton(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("منح إذن الإشعارات (Android 13+)")
                            }
                        }

                        PreferenceToggleRow(
                            label = "تفعيل الإشعارات العامة",
                            checked = notificationPrefs.notificationsEnabled,
                            tag = "switch_notifications_master"
                        ) { enabled ->
                            onUpdateNotificationPrefs { it.copy(notificationsEnabled = enabled) }
                        }
                        PreferenceToggleRow(
                            label = "تشغيل صوت الإشعارات",
                            checked = notificationPrefs.soundEnabled,
                            tag = "switch_notifications_sound"
                        ) { sound ->
                            onUpdateNotificationPrefs { it.copy(soundEnabled = sound) }
                        }
                        PreferenceToggleRow(
                            label = "إشعارات الفواتير الجديدة",
                            checked = notificationPrefs.invoiceNotificationsEnabled,
                            tag = "switch_notifications_invoice"
                        ) { enabled ->
                            onUpdateNotificationPrefs { it.copy(invoiceNotificationsEnabled = enabled) }
                        }
                        PreferenceToggleRow(
                            label = "إشعارات التحصيلات",
                            checked = notificationPrefs.collectionNotificationsEnabled,
                            tag = "switch_notifications_collection"
                        ) { enabled ->
                            onUpdateNotificationPrefs { it.copy(collectionNotificationsEnabled = enabled) }
                        }
                        PreferenceToggleRow(
                            label = "إشعارات مواعيد القراءة",
                            checked = notificationPrefs.readingRemindersEnabled,
                            tag = "switch_notifications_reading"
                        ) { enabled ->
                            onUpdateNotificationPrefs { it.copy(readingRemindersEnabled = enabled) }
                        }

                        OutlinedButton(
                            onClick = { onSendTestNotification(context) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("test_notification_button")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("إرسال إشعار تجريبي الآن")
                        }
                    }
                }
            }

            item(key = "schedule_reminder_form") {
                Card(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, alasalColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("جدولة موعد قراءة عداد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SubscriberPickerTriggerButton(
                            selectedSubscriber = selectedSubscriberId?.let { subMap[it] },
                            onClick = { showSubscriberPickerDialog = true },
                            allSelectedLabel = "جولة عامة لجميع المشتركين (${subscribers.size})",
                            testTag = "reminders_subscriber_picker_button"
                        )
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("عنوان الموعد / التذكير *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reminder_title_input")
                        )
                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text("ملاحظات الموعد") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reminder_notes_input")
                        )

                        Text("وقت التذكير: ${dateFormat.format(Date(scheduledAt))}", style = MaterialTheme.typography.bodySmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(
                                listOf(
                                    0L to "فوراً",
                                    3_600_000L to "بعد ساعة",
                                    86_400_000L to "غداً",
                                    7 * 86_400_000L to "بعد أسبوع"
                                )
                            ) { (ms, label) ->
                                FilterChip(
                                    selected = delayMillis == ms,
                                    onClick = { delayMillis = ms },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val subName = selectedSubscriberId?.let { subMap[it]?.fullName } ?: "جميع المشتركين"
                                val msg = "موعد قراءة العداد ($subName) ${if (notesInput.isNotBlank()) "— $notesInput" else ""}"
                                onScheduleReminder(
                                    context,
                                    selectedSubscriberId,
                                    titleInput,
                                    msg,
                                    notesInput,
                                    scheduledAt
                                ) {
                                    notesInput = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("schedule_reminder_button")
                        ) {
                            Icon(Icons.Filled.AlarmAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("حفظ وجدولة التذكير")
                        }
                    }
                }
            }

            item(key = "reminders_list_header") {
                Text(
                    text = "المواعيد والتذكيرات المسجلة (${reminders.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()
                )
            }

            if (reminders.isEmpty()) {
                item(key = "empty_reminders") {
                    EmptyStateCard(
                        icon = Icons.Filled.NotificationsActive,
                        title = "لا توجد تذكيرات مجدولة",
                        description = "أضف موعد قراءة جديد وسيتم متابعته وتنبيهك عبر WorkManager.",
                        modifier = Modifier.widthIn(max = 800.dp)
                    )
                }
            } else {
                items(reminders, key = { it.id }) { rem ->
                    val subName = rem.subscriberId?.let { subMap[it]?.fullName } ?: "جميع المشتركين"
                    val isDone = rem.status == "COMPLETED"
                    Card(
                        modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth().testTag("reminder_card_${rem.id}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${rem.title} ($subName)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = if (isDone) alasalColors.creditContainer else alasalColors.gold.copy(alpha = 0.18f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = when (rem.status) {
                                            "COMPLETED" -> "مكتمل"
                                            "SNOOZED" -> "مؤجل (${rem.snoozeCount})"
                                            "NOTIFIED" -> "تم التنبيه"
                                            else -> "قيد الانتظار"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDone) alasalColors.creditGreen else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "الموعد: ${dateFormat.format(Date(rem.scheduledAt))}${if (rem.notes.isNotBlank()) " | ${rem.notes}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!isDone) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onCompleteReminder(rem.id) },
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("reminder_complete_btn_${rem.id}")
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text("إكمال")
                                    }
                                    OutlinedButton(
                                        onClick = { onSnoozeReminder(context, rem.id, 3_600_000L) },
                                        modifier = Modifier.weight(1f).height(48.dp).testTag("reminder_snooze_btn_${rem.id}")
                                    ) {
                                        Icon(Icons.Filled.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text("تأجيل (+ساعة)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubscriberPickerDialog) {
        AvailableSubscribersDialog(
            subscribers = subscribers,
            selectedSubscriberId = selectedSubscriberId,
            onSelectSubscriber = { sub -> selectedSubscriberId = sub.id },
            onDismiss = { showSubscriberPickerDialog = false },
            allowAllOption = true,
            allOptionTitle = "جولة عامة لجميع المشتركين",
            onSelectAll = { selectedSubscriberId = null }
        )
    }
}

@Composable
private fun PreferenceToggleRow(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
