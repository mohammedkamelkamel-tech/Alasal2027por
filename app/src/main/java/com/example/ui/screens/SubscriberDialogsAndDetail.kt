package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.SubscriberFullHistory
import com.example.data.repository.SubscriberInput
import com.example.data.repository.TariffPricePreviewItem
import com.example.domain.finance.StationReportGenerator
import com.example.service.DocumentPdfAndThermalService
import com.example.service.InvoiceShareAndPrintService
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.theme.LocalAlasalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubscriberFormDialog(
    initialSubscriber: SubscriberEntity?,
    defaultPricePerKwh: Double,
    onDismiss: () -> Unit,
    onSave: (SubscriberInput) -> Unit
) {
    var subscriberCode by remember { mutableStateOf(initialSubscriber?.subscriberCode ?: "") }
    var fullName by remember { mutableStateOf(initialSubscriber?.fullName ?: "") }
    var phoneNumber by remember { mutableStateOf(initialSubscriber?.phoneNumber ?: "") }
    var address by remember { mutableStateOf(initialSubscriber?.address ?: "") }
    var meterNumber by remember { mutableStateOf(initialSubscriber?.meterNumber ?: "") }
    var meterSealNumber by remember { mutableStateOf(initialSubscriber?.meterSealNumber ?: "") }
    var subscriptionType by remember { mutableStateOf(initialSubscriber?.subscriptionType ?: "منزلي") }
    var customPrice by remember {
        mutableStateOf((initialSubscriber?.customKwhPrice ?: defaultPricePerKwh).toString())
    }
    var initialCredit by remember { mutableStateOf("0") }
    var isActive by remember { mutableStateOf(initialSubscriber?.isActive ?: true) }
    var notes by remember { mutableStateOf(initialSubscriber?.notes ?: "") }
    var subscriptionDaysOffset by remember { mutableIntStateOf(0) }

    val baseDate = remember(initialSubscriber?.id) {
        initialSubscriber?.subscriptionDate ?: System.currentTimeMillis()
    }
    val effectiveSubscriptionDate = baseDate - subscriptionDaysOffset * 86_400_000L
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (initialSubscriber == null) "إضافة مشترك جديد" else "تعديل بيانات المشترك",
                    style = MaterialTheme.typography.titleLarge
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("اسم المشترك *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sub_input_name")
                    )
                    OutlinedTextField(
                        value = subscriberCode,
                        onValueChange = { subscriberCode = it },
                        label = { Text("رقم المشترك *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sub_input_code")
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("رقم الهاتف") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sub_input_phone")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("العنوان") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sub_input_address")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    OutlinedTextField(
                        value = meterNumber,
                        onValueChange = { meterNumber = it },
                        label = { Text("رقم العداد *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("sub_input_meter")
                    )
                    OutlinedTextField(
                        value = meterSealNumber,
                        onValueChange = { meterSealNumber = it },
                        label = { Text("رقم ختم العداد") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("sub_input_seal")
                    )
                }
                OutlinedTextField(
                    value = customPrice,
                    onValueChange = { customPrice = it },
                    label = { Text("سعر الكيلوواط للمشترك *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("sub_input_price")
                )
                if (initialSubscriber == null) {
                    OutlinedTextField(
                        value = initialCredit,
                        onValueChange = { initialCredit = it },
                        label = { Text("الرصيد الدائن الافتتاحي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sub_input_credit")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("منزلي", "تجاري", "زراعي").forEach { type ->
                        FilterChip(
                            selected = subscriptionType == type,
                            onClick = { subscriptionType = type },
                            label = { Text(type) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تاريخ الاشتراك: ${dateFormat.format(Date(effectiveSubscriptionDate))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { subscriptionDaysOffset += 1 }) {
                        Text("تغيير (-يوم)")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "حالة الاشتراك (نشط)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("إلغاء") }
                    Button(
                        onClick = {
                            onSave(
                                SubscriberInput(
                                    id = initialSubscriber?.id ?: 0L,
                                    subscriberCode = subscriberCode,
                                    fullName = fullName,
                                    phoneNumber = phoneNumber,
                                    meterNumber = meterNumber,
                                    meterSealNumber = meterSealNumber,
                                    address = address,
                                    subscriptionType = subscriptionType,
                                    customKwhPrice = customPrice.toDoubleOrNull() ?: defaultPricePerKwh,
                                    subscriptionDate = effectiveSubscriptionDate,
                                    isActive = isActive,
                                    initialCreditBalance = initialCredit.toDoubleOrNull() ?: 0.0,
                                    notes = notes
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("sub_save_button")
                    ) {
                        Text("حفظ")
                    }
                }
            }
        }
    }
}

@Composable
fun BulkTariffManagementDialog(
    subscribers: List<SubscriberEntity>,
    previewItems: List<TariffPricePreviewItem>,
    onPreparePreview: (List<Long>?, Double) -> Unit,
    onClearPreview: () -> Unit,
    onApplyUpdate: (List<Long>?, Double, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    val alasalColors = LocalAlasalColors.current
    var scopeMode by remember { mutableIntStateOf(0) } // 0 = All, 1 = Selected Group, 2 = Single
    val selectedIds = remember { mutableStateListOf<Long>() }
    var newPriceInput by remember { mutableStateOf("") }
    var reasonInput by remember { mutableStateOf("") }
    var effectiveDaysOffset by remember { mutableIntStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")) }
    val baseNow = remember { System.currentTimeMillis() }
    val effectiveDate = baseNow + effectiveDaysOffset * 86_400_000L

    val targetIds: List<Long>? = when (scopeMode) {
        0 -> null
        else -> selectedIds.toList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إدارة وتعديل أسعار الكيلوواط",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                Text(
                    text = "تغيير السعر يطبق على الفواتير الجديدة فقط ولا يعدل الفواتير القديمة تلقائياً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = scopeMode == 0,
                        onClick = { scopeMode = 0; onClearPreview() },
                        label = { Text("الجميع") },
                        modifier = Modifier.testTag("tariff_scope_all")
                    )
                    FilterChip(
                        selected = scopeMode == 1,
                        onClick = { scopeMode = 1; onClearPreview() },
                        label = { Text("مجموعة") },
                        modifier = Modifier.testTag("tariff_scope_group")
                    )
                    FilterChip(
                        selected = scopeMode == 2,
                        onClick = {
                            scopeMode = 2
                            if (selectedIds.size > 1) {
                                val first = selectedIds.first()
                                selectedIds.clear()
                                selectedIds.add(first)
                            }
                            onClearPreview()
                        },
                        label = { Text("مشترك واحد") },
                        modifier = Modifier.testTag("tariff_scope_single")
                    )
                }

                if (scopeMode != 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(6.dp)) {
                            items(subscribers, key = { it.id }) { sub ->
                                val checked = selectedIds.contains(sub.id)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            if (scopeMode == 2) {
                                                selectedIds.clear()
                                                if (isChecked) selectedIds.add(sub.id)
                                            } else {
                                                if (isChecked) selectedIds.add(sub.id) else selectedIds.remove(sub.id)
                                            }
                                            onClearPreview()
                                        }
                                    )
                                    Text(
                                        text = "${sub.fullName} (السعر الحالي: ${sub.customKwhPrice})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newPriceInput,
                    onValueChange = { newPriceInput = it; onClearPreview() },
                    label = { Text("سعر الكيلوواط الجديد *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tariff_new_price_input")
                )

                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("سبب التعديل *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("tariff_reason_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تاريخ السريان: ${dateFormat.format(Date(effectiveDate))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { effectiveDaysOffset += 1 }) {
                        Text("+1 يوم")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val parsed = newPriceInput.toDoubleOrNull() ?: -1.0
                        onPreparePreview(targetIds, parsed)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tariff_preview_button")
                ) {
                    Icon(Icons.Filled.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("معاينة السعر القديم والجديد قبل الاعتماد")
                }

                if (previewItems.isNotEmpty()) {
                    Surface(
                        color = alasalColors.gold.copy(alpha = 0.14f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().testTag("tariff_preview_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "معاينة التأثير (${previewItems.size} مشترك):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            previewItems.take(5).forEach { item ->
                                Text(
                                    text = "• ${item.subscriberName}: من ${item.oldPrice} ← إلى ${item.newPrice}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (previewItems.size > 5) {
                                Text(
                                    text = "... بالإضافة إلى ${previewItems.size - 5} مشتركين آخرين",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val parsed = newPriceInput.toDoubleOrNull() ?: return@Button
                            onApplyUpdate(targetIds, parsed, effectiveDate, reasonInput)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tariff_confirm_apply_button")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("اعتماد السعر الجديد وتسجيله في التاريخ")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriberDetailDialog(
    history: SubscriberFullHistory,
    notificationPrefs: NotificationAndPrintPrefs = NotificationAndPrintPrefs(),
    onUpdateKwhPrice: (newPrice: Double, reason: String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val sub = history.subscriber
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }
    val tabs = listOf("كشف الحساب", "الفواتير", "التحصيلات", "القراءات", "الأسعار")

    var inlinePriceInput by remember(sub.id, sub.customKwhPrice) {
        mutableStateOf(sub.customKwhPrice.toString())
    }
    var inlineFeedback by remember(sub.id) { mutableStateOf<Pair<Boolean, String>?>(null) }

    val collectionsWithReceipts = remember(history) {
        val rcpMap = history.receipts.associateBy { it.collectionId }
        history.collections.map { col -> CollectionWithReceipt(col, rcpMap[col.id]) }
    }
    val statement = remember(history) {
        StationReportGenerator.buildSubscriberStatement(
            subscriber = sub,
            invoices = history.invoices,
            collectionsWithReceipts = collectionsWithReceipts,
            readings = history.meterReadings,
            creditTransactions = history.creditBalances
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = sub.fullName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "رقم: ${sub.subscriberCode} | عداد: ${sub.meterNumber} | ختم: ${sub.meterSealNumber.ifEmpty { "-" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = alasalColors.creditContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("الرصيد الدائن", style = MaterialTheme.typography.labelSmall, color = alasalColors.creditGreen)
                            Text(
                                "${sub.currentCreditBalance}",
                                style = MaterialTheme.typography.titleSmall,
                                color = alasalColors.creditGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        color = alasalColors.arrearsContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("المتأخرات", style = MaterialTheme.typography.labelSmall, color = alasalColors.arrearsRed)
                            Text(
                                "${sub.currentArrearsAmount}",
                                style = MaterialTheme.typography.titleSmall,
                                color = alasalColors.arrearsRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    color = alasalColors.gold.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.PriceChange, contentDescription = null, tint = alasalColors.gold, modifier = Modifier.size(18.dp))
                            Text(
                                text = "تعديل سعر الكيلوواط مباشرة من ملف المشترك",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inlinePriceInput,
                                onValueChange = {
                                    inlinePriceInput = it
                                    inlineFeedback = null
                                },
                                label = { Text("السعر (للفواتير الجديدة)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("detail_kwh_price_input")
                            )
                            Button(
                                onClick = {
                                    val parsed = inlinePriceInput.toDoubleOrNull()
                                    if (parsed == null || parsed <= 0.0) {
                                        inlineFeedback = false to "أدخل سعر كيلوواط صحيحاً أكبر من صفر."
                                    } else {
                                        onUpdateKwhPrice(parsed, "تعديل مباشر من ملف المشترك")
                                        inlineFeedback = true to "تم تحديث السعر إلى $parsed للفواتير المستقبلية فقط."
                                    }
                                },
                                modifier = Modifier.height(50.dp).testTag("detail_kwh_price_save_button")
                            ) {
                                Text("حفظ السعر", maxLines = 1)
                            }
                        }
                        Text(
                            text = "يطبق على الفواتير الجديدة فقط ولا يمس الفواتير السابقة.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (inlineFeedback != null) {
                    val (isOk, msg) = inlineFeedback!!
                    Surface(
                        color = if (isOk) alasalColors.creditContainer else alasalColors.arrearsContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().testTag("detail_inline_feedback")
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOk) alasalColors.creditGreen else alasalColors.arrearsRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val pdf = DocumentPdfAndThermalService.generateStatementA4Pdf(
                                context = context,
                                statement = statement,
                                stationAddress = notificationPrefs.stationAddress,
                                stationPhone = notificationPrefs.stationPhone
                            )
                            DocumentPdfAndThermalService.shareFile(context, pdf, "application/pdf", "مشاركة كشف الحساب PDF")
                            inlineFeedback = true to "تم تصدير كشف الحساب PDF للمشاركة"
                        },
                        modifier = Modifier.weight(1f).height(46.dp).testTag("sub_statement_pdf_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("PDF", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val pdf = DocumentPdfAndThermalService.generateStatementA4Pdf(
                                context = context,
                                statement = statement,
                                stationAddress = notificationPrefs.stationAddress,
                                stationPhone = notificationPrefs.stationPhone
                            )
                            DocumentPdfAndThermalService.printPdfFile(context, pdf, "كشف_حساب_${sub.subscriberCode}")
                            inlineFeedback = true to "تم إرسال كشف الحساب للطباعة A4"
                        },
                        modifier = Modifier.weight(1f).height(46.dp).testTag("sub_statement_print_btn")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("طباعة", maxLines = 1)
                    }
                    Button(
                        onClick = {
                            val warn = InvoiceShareAndPrintService.sendStatementViaWhatsApp(
                                context = context,
                                statement = statement,
                                defaultCountryCode = notificationPrefs.defaultCountryCode
                            )
                            inlineFeedback = if (warn != null) {
                                false to warn
                            } else {
                                true to "تم فتح واتساب لإرسال كشف حساب ${sub.fullName}"
                            }
                        },
                        modifier = Modifier.weight(1f).height(46.dp).testTag("sub_statement_whatsapp_btn")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("واتساب", maxLines = 1)
                    }
                }

                PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 4.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 180.dp, max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            item {
                                Text(
                                    text = "ملخص كشف الحساب المالي:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "إجمالي الاستهلاك: ${statement.totalConsumedKwh} ك.و.س | الفواتير: ${history.invoices.size} | التحصيلات: ${statement.totalCollected} | الصافي النهائي: ${statement.netFinalBalance}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            }
                            items(history.creditBalances.sortedByDescending { it.recordedAt }) { cred ->
                                Surface(
                                    color = alasalColors.creditContainer.copy(alpha = 0.45f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(
                                            text = "حركة دائن (${cred.transactionType}): ${cred.amountDelta}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = alasalColors.creditGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${cred.notes} — الرصيد بعدها: ${cred.balanceAfter}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            items(history.auditLogs.sortedByDescending { it.createdAt }) { log ->
                                Text(
                                    text = "• ${dateFormat.format(Date(log.createdAt))}: ${log.detailsAr}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        1 -> {
                            items(history.invoices.sortedByDescending { it.issuedAt }) { inv ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(
                                            text = "${inv.invoiceNumber} (${InvoiceShareAndPrintService.translateStatus(inv.status)})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "استهلاك: ${inv.consumedKwh} × ${inv.unitPriceApplied} | الصافي: ${inv.netPayableAmount} | المتبقي: ${inv.remainingAmount}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            items(history.collections.sortedByDescending { it.collectedAt }) { col ->
                                val receipt = history.receipts.firstOrNull { it.collectionId == col.id }
                                Surface(
                                    color = alasalColors.creditContainer.copy(alpha = 0.4f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(
                                            text = "تحصيل ${col.collectionReference} — إيصال ${receipt?.receiptNumber ?: "-"}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "المبلغ: ${col.amountPaid} (للفاتورة: ${col.appliedToInvoice} | فائض دائن: ${col.addedToCredit})",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            items(history.meterReadings.sortedByDescending { it.readingDate }) { r ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(
                                            text = "فترة ${r.billingPeriod} (${dateFormat.format(Date(r.readingDate))})",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "من ${r.previousReading} إلى ${r.currentReading} | الاستهلاك: ${r.consumedKwh} ك.و.س",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        4 -> {
                            item {
                                Surface(
                                    color = alasalColors.gold.copy(alpha = 0.16f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(
                                            text = "السعر الحالي: ${sub.customKwhPrice} / ك.و.س",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "السعر السابق: ${sub.previousKwhPrice ?: "لا يوجد تغيير سابق"}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            items(history.tariffChangeLogs.sortedByDescending { it.changedAt }) { tLog ->
                                Text(
                                    text = "• ${dateFormat.format(Date(tLog.changedAt))}: من ${tLog.oldPricePerKwh} إلى ${tLog.newPricePerKwh} بواسطة ${tLog.actorNameSnapshot} (${tLog.reason})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
