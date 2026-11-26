package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.repository.MeterReadingInput
import com.example.data.repository.MeterReadingUpdateInput
import com.example.domain.finance.ConsumptionSummaryCalculator
import com.example.domain.finance.FinancialCalculator
import com.example.service.InvoiceShareAndPrintService
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeterReadingsScreen(
    subscribers: List<SubscriberEntity>,
    readings: List<MeterReadingEntity>,
    invoices: List<InvoiceEntity>,
    feedback: FeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onSaveReading: (MeterReadingInput, Boolean, String, () -> Unit) -> Unit,
    onUpdateReading: (MeterReadingUpdateInput, () -> Unit) -> Unit = { _, done -> done() },
    onDeleteReading: (Long, () -> Unit) -> Unit = { _, done -> done() },
    onIssueInvoiceForReading: (Long) -> Unit,
    notificationPrefs: NotificationAndPrintPrefs = NotificationAndPrintPrefs(),
    lastIssuedInvoice: InvoiceEntity? = null,
    onClearLastIssuedInvoice: () -> Unit = {},
    onFeedbackWarning: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val arabicLocale = Locale.forLanguageTag("ar")
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", arabicLocale)

    var selectedSubscriberId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(subscribers) {
        if (selectedSubscriberId == null && subscribers.isNotEmpty()) {
            selectedSubscriberId = subscribers.first().id
        } else if (selectedSubscriberId != null && subscribers.none { it.id == selectedSubscriberId }) {
            selectedSubscriberId = subscribers.firstOrNull()?.id
        }
    }

    val selectedSubscriber = subscribers.firstOrNull { it.id == selectedSubscriberId }
    val subscriberLatestReading = remember(readings, selectedSubscriberId) {
        readings.filter { it.subscriberId == selectedSubscriberId }.maxByOrNull { it.readingDate }
    }
    val previousReadingValue = subscriberLatestReading?.currentReading ?: selectedSubscriber?.initialMeterReading ?: 0.0

    var currentReadingText by remember { mutableStateOf("") }
    var readingDaysOffset by remember { mutableIntStateOf(0) }
    var notes by remember { mutableStateOf("") }
    var photoUriString by remember { mutableStateOf<String?>(null) }
    var allowLowOverride by remember { mutableStateOf(false) }
    var overrideReason by remember { mutableStateOf("") }
    var editingReading by remember { mutableStateOf<MeterReadingEntity?>(null) }
    var deletingReading by remember { mutableStateOf<MeterReadingEntity?>(null) }

    val baseReadingTime = remember { System.currentTimeMillis() }
    val readingDate = baseReadingTime - readingDaysOffset * 86_400_000L
    val previousReadingDate = subscriberLatestReading?.readingDate ?: selectedSubscriber?.subscriptionDate ?: readingDate
    val periodKeyFormatter = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val billingPeriod = periodKeyFormatter.format(Date(readingDate))
    val autoDetectedHalf = remember(readingDate) {
        ConsumptionSummaryCalculator.classifyHalfFromDate(readingDate)
    }
    var selectedReadingHalf by remember(autoDetectedHalf) { mutableIntStateOf(autoDetectedHalf) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedSubscriber != null) {
            val copied = InvoiceShareAndPrintService.copyMeterPhotoToPrivateStorage(
                context = context,
                sourceUri = uri,
                subscriberCode = selectedSubscriber.subscriberCode
            )
            photoUriString = copied ?: uri.toString()
        }
    }

    val parsedCurrent = currentReadingText.toDoubleOrNull()
    val isLowerThanPrevious = parsedCurrent != null && parsedCurrent < previousReadingValue
    val calculatedConsumption = remember(parsedCurrent, previousReadingValue, allowLowOverride) {
        if (parsedCurrent == null || parsedCurrent < 0.0) {
            null
        } else if (parsedCurrent < previousReadingValue && !allowLowOverride) {
            null
        } else {
            FinancialCalculator.calculateConsumption(
                previousReading = previousReadingValue,
                currentReading = parsedCurrent,
                allowLowReadingOverride = allowLowOverride
            )
        }
    }

    val subscriberMap = remember(subscribers) { subscribers.associateBy { it.id } }
    val invoicedReadingIds = remember(invoices) { invoices.mapNotNull { it.meterReadingId }.toSet() }

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
                .testTag("readings_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "title") {
                Column(modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                    Text(
                        text = "قراءات العدادات وإرفاق الصور",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "اختر المشترك لعرض قراءته السابقة تلقائياً، وحساب الاستهلاك، وإصدار الفاتورة مباشرة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (lastIssuedInvoice != null) {
                item(key = "instant_whatsapp_dispatch") {
                    val issuedSub = subscriberMap[lastIssuedInvoice.subscriberId]
                    var dispatchWarning by remember(lastIssuedInvoice.id) { mutableStateOf<String?>(null) }
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .testTag("instant_invoice_whatsapp_banner"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = alasalColors.gold.copy(alpha = 0.14f)),
                        border = BorderStroke(1.2.dp, alasalColors.gold.copy(alpha = 0.7f))
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
                                Text(
                                    text = "إرسال الفاتورة الصادرة (${lastIssuedInvoice.invoiceNumber}) فوراً",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = onClearLastIssuedInvoice) {
                                    Text("إخفاء")
                                }
                            }
                            Text(
                                text = "المشترك: ${issuedSub?.fullName ?: "-"} | الصافي المستحق: ${lastIssuedInvoice.netPayableAmount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (dispatchWarning != null) {
                                Text(
                                    text = dispatchWarning!!,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = alasalColors.arrearsRed,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("reading_whatsapp_warning")
                                )
                            }
                            Button(
                                onClick = {
                                    val warn = InvoiceShareAndPrintService.sendViaWhatsApp(
                                        context = context,
                                        invoice = lastIssuedInvoice,
                                        subscriber = issuedSub,
                                        defaultCountryCode = notificationPrefs.defaultCountryCode
                                    )
                                    if (warn != null) {
                                        dispatchWarning = warn
                                        onFeedbackWarning(warn)
                                    } else {
                                        dispatchWarning = null
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("reading_instant_whatsapp_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("إرسال الفاتورة عبر واتساب فوراً")
                            }
                        }
                    }
                }
            }

            if (subscribers.isEmpty()) {
                item(key = "no_subscribers") {
                    EmptyStateCard(
                        icon = Icons.Filled.ElectricMeter,
                        title = "أضف مشتركاً أولاً لتسجيل القراءات",
                        description = "انتقل إلى تبويب المشتركين لإضافة أول مشترك، ثم عد هنا لتسجيل قراءات العداد وإصدار الفواتير.",
                        modifier = Modifier.widthIn(max = 800.dp),
                        testTag = "readings_empty_subscribers"
                    )
                }
            } else {
                item(key = "entry_form") {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .testTag("reading_entry_card"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "اختيار المشترك:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            LazyRow(
                                modifier = Modifier.testTag("reading_sub_chips_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(subscribers, key = { it.id }) { sub ->
                                    FilterChip(
                                        selected = sub.id == selectedSubscriberId,
                                        onClick = {
                                            selectedSubscriberId = sub.id
                                            allowLowOverride = false
                                            overrideReason = ""
                                        },
                                        label = { Text("${sub.fullName} (${sub.subscriberCode})") },
                                        modifier = Modifier.testTag("reading_sub_chip_${sub.subscriberCode}")
                                    )
                                }
                            }

                            if (selectedSubscriber != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "القراءة السابقة: $previousReadingValue",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "السعر: ${selectedSubscriber.customKwhPrice} / ك.و.س",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = alasalColors.gold,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = currentReadingText,
                                    onValueChange = { currentReadingText = it },
                                    label = { Text("القراءة الحالية *") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("reading_current_input")
                                )
                                Text(
                                    text = "فترة الاستهلاك: من ${dateFormat.format(Date(previousReadingDate))} إلى ${dateFormat.format(Date(readingDate))}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = alasalColors.gold,
                                    modifier = Modifier.fillMaxWidth().testTag("reading_period_range")
                                )
                            }

                            if (calculatedConsumption != null) {
                                Surface(
                                    color = alasalColors.creditContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "الاستهلاك المحسوب: $calculatedConsumption ك.و.س",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = alasalColors.creditGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    )
                                }
                            }

                            if (isLowerThanPrevious) {
                                Surface(
                                    color = alasalColors.arrearsContainer,
                                    shape = MaterialTheme.shapes.small,
                                    border = BorderStroke(1.dp, alasalColors.arrearsRed.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = alasalColors.arrearsRed)
                                            Text(
                                                text = "تحذير: القراءة الحالية أقل من السابقة ($previousReadingValue)",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = alasalColors.arrearsRed
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = allowLowOverride,
                                                onCheckedChange = { allowLowOverride = it },
                                                modifier = Modifier.testTag("reading_low_override_checkbox")
                                            )
                                            Text(
                                                text = "تأكيد وصلاحية المدير (تبديل/تصفير عداد)",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        if (allowLowOverride) {
                                            OutlinedTextField(
                                                value = overrideReason,
                                                onValueChange = { overrideReason = it },
                                                label = { Text("سبب التجاوز / رقم العداد الجديد *") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().testTag("reading_override_reason_input")
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "تاريخ القراءة: ${dateFormat.format(Date(readingDate))} ($billingPeriod)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(onClick = { readingDaysOffset += 1 }) {
                                        Text("-يوم")
                                    }
                                    if (readingDaysOffset != 0) {
                                        TextButton(onClick = { readingDaysOffset = 0 }) {
                                            Text("اليوم")
                                        }
                                    }
                                    TextButton(onClick = { readingDaysOffset -= 1 }) {
                                        Text("+يوم")
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "دورة القراءة نصف الشهرية (تلقائي حسب التاريخ ويمكن التعديل):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedReadingHalf == ConsumptionSummaryCalculator.READING_HALF_FIRST,
                                        onClick = { selectedReadingHalf = ConsumptionSummaryCalculator.READING_HALF_FIRST },
                                        label = { Text("النصف الأول (1-15)") },
                                        modifier = Modifier.weight(1f).testTag("reading_half_chip_1")
                                    )
                                    FilterChip(
                                        selected = selectedReadingHalf == ConsumptionSummaryCalculator.READING_HALF_SECOND,
                                        onClick = { selectedReadingHalf = ConsumptionSummaryCalculator.READING_HALF_SECOND },
                                        label = { Text("النصف الثاني (16-آخر الشهر)") },
                                        modifier = Modifier.weight(1f).testTag("reading_half_chip_2")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("ملاحظات القراءة") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reading_attach_photo_button")
                            ) {
                                Icon(
                                    imageVector = if (photoUriString != null) Icons.Filled.CheckCircle else Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = if (photoUriString != null) "تم إرفاق صورة العداد (اضغط للتغيير)" else "إرفاق صورة العداد (Photo Picker)"
                                )
                            }

                            val clearForm = {
                                currentReadingText = ""
                                notes = ""
                                photoUriString = null
                                allowLowOverride = false
                                overrideReason = ""
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val subId = selectedSubscriberId ?: return@OutlinedButton
                                        val valCurrent = parsedCurrent ?: -1.0
                                        onSaveReading(
                                            MeterReadingInput(
                                                subscriberId = subId,
                                                billingPeriod = billingPeriod,
                                                currentReading = valCurrent,
                                                readingDate = readingDate,
                                                photoUri = photoUriString,
                                                allowLowReadingOverride = allowLowOverride,
                                                overrideReason = overrideReason,
                                                notes = notes,
                                                readingHalf = selectedReadingHalf
                                            ),
                                            false,
                                            "",
                                            clearForm
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("reading_save_only_button")
                                ) {
                                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(stringResource(R.string.action_save_reading), maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        val subId = selectedSubscriberId ?: return@Button
                                        val valCurrent = parsedCurrent ?: -1.0
                                        onSaveReading(
                                            MeterReadingInput(
                                                subscriberId = subId,
                                                billingPeriod = billingPeriod,
                                                currentReading = valCurrent,
                                                readingDate = readingDate,
                                                photoUri = photoUriString,
                                                allowLowReadingOverride = allowLowOverride,
                                                overrideReason = overrideReason,
                                                notes = notes,
                                                readingHalf = selectedReadingHalf
                                            ),
                                            true,
                                            notes,
                                            clearForm
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("reading_save_and_invoice_button")
                                ) {
                                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("حفظ وإصدار فاتورة", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "history_header") {
                Text(
                    text = "سجل قراءات العدادات (${readings.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()
                )
            }

            items(readings, key = { it.id }) { reading ->
                val sub = subscriberMap[reading.subscriberId]
                val hasInvoice = invoicedReadingIds.contains(reading.id)
                Card(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .testTag("reading_card_${reading.id}"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
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
                                text = "${sub?.fullName ?: "مشترك #${reading.subscriberId}"} — فترة ${reading.billingPeriod}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${ConsumptionSummaryCalculator.halfShortTitle(reading.readingHalf)} | ${dateFormat.format(Date(reading.readingDate))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = alasalColors.gold,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "السابقة: ${reading.previousReading} ← الحالية: ${reading.currentReading} | الاستهلاك: ${reading.consumedKwh} ك.و.س",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (reading.photoUri != null) {
                            Text(
                                text = "📷 مرفق صورة العداد محفوظة محلياً",
                                style = MaterialTheme.typography.labelSmall,
                                color = alasalColors.creditGreen
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { editingReading = reading },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("reading_edit_button_${reading.id}")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("تعديل", maxLines = 1)
                            }
                            if (!hasInvoice) {
                                OutlinedButton(
                                    onClick = { deletingReading = reading },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("reading_delete_button_${reading.id}"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = alasalColors.arrearsRed)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("حذف", maxLines = 1)
                                }
                            }
                        }
                        if (!hasInvoice) {
                            OutlinedButton(
                                onClick = { onIssueInvoiceForReading(reading.id) },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("إصدار فاتورة من هذه القراءة")
                            }
                        } else {
                            Text(
                                text = "✓ صدرت لها فاتورة",
                                style = MaterialTheme.typography.labelMedium,
                                color = alasalColors.creditGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    editingReading?.let { target ->
        var editPeriod by remember(target.id) { mutableStateOf(target.billingPeriod) }
        var editHalf by remember(target.id) { mutableIntStateOf(target.readingHalf) }
        var editPreviousText by remember(target.id) { mutableStateOf(target.previousReading.toString()) }
        var editCurrentText by remember(target.id) { mutableStateOf(target.currentReading.toString()) }
        var editNotes by remember(target.id) { mutableStateOf(target.notes) }

        AlertDialog(
            onDismissRequest = { editingReading = null },
            title = { Text("تعديل قراءة العداد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editPeriod,
                        onValueChange = { editPeriod = it },
                        label = { Text("الشهر (YYYY-MM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_reading_period_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = editHalf == ConsumptionSummaryCalculator.READING_HALF_FIRST,
                            onClick = { editHalf = ConsumptionSummaryCalculator.READING_HALF_FIRST },
                            label = { Text("النصف الأول") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = editHalf == ConsumptionSummaryCalculator.READING_HALF_SECOND,
                            onClick = { editHalf = ConsumptionSummaryCalculator.READING_HALF_SECOND },
                            label = { Text("النصف الثاني") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editPreviousText,
                        onValueChange = { editPreviousText = it },
                        label = { Text("القراءة السابقة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_reading_previous_input")
                    )
                    OutlinedTextField(
                        value = editCurrentText,
                        onValueChange = { editCurrentText = it },
                        label = { Text("القراءة الحالية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_reading_current_input")
                    )
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("ملاحظات") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prevVal = editPreviousText.toDoubleOrNull() ?: return@Button
                        val currVal = editCurrentText.toDoubleOrNull() ?: return@Button
                        onUpdateReading(
                            MeterReadingUpdateInput(
                                readingId = target.id,
                                billingPeriod = editPeriod,
                                readingHalf = editHalf,
                                previousReading = prevVal,
                                currentReading = currVal,
                                readingDate = target.readingDate,
                                notes = editNotes
                            )
                        ) { editingReading = null }
                    },
                    modifier = Modifier.testTag("edit_reading_confirm_button")
                ) {
                    Text("حفظ التعديل")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingReading = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    deletingReading?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingReading = null },
            title = { Text("تأكيد حذف القراءة") },
            text = {
                Text("هل تريد حذف قراءة الفترة ${target.billingPeriod} (${ConsumptionSummaryCalculator.halfShortTitle(target.readingHalf)}) باستهلاك ${target.consumedKwh} ك.و.س؟ سيتم تحديث إجمالي الاستهلاك فوراً.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteReading(target.id) { deletingReading = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = alasalColors.arrearsRed),
                    modifier = Modifier.testTag("delete_reading_confirm_button")
                ) {
                    Text("تأكيد الحذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingReading = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
