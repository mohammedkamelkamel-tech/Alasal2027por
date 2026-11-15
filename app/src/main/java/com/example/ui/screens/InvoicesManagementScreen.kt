package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.UserEntity
import com.example.data.repository.InvoiceDetailBundle
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
fun InvoicesManagementScreen(
    invoices: List<InvoiceEntity>,
    subscribers: List<SubscriberEntity>,
    selectedInvoiceDetail: InvoiceDetailBundle?,
    feedback: FeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onSelectInvoice: (Long) -> Unit,
    onDismissDetail: () -> Unit,
    onRecordPayment: (Long, Double, String, Long?, () -> Unit) -> Unit,
    onRepriceInvoice: (Long, Double, String, () -> Unit) -> Unit,
    onDeleteInvoice: (Long, () -> Unit) -> Unit = { _, _ -> },
    users: List<UserEntity> = emptyList(),
    collectionsWithReceipts: List<CollectionWithReceipt> = emptyList(),
    onSelectReceipt: (ReceiptEntity) -> Unit = {},
    notificationPrefs: NotificationAndPrintPrefs = NotificationAndPrintPrefs(),
    onFeedbackWarning: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")) }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var dateWindowDays by remember { mutableIntStateOf(0) } // 0 = All, 7 = 7 days, 30 = 30 days
    var selectedSubscriberForDetails by remember { mutableStateOf<SubscriberEntity?>(null) }
    var smsSummary by remember { mutableStateOf<String?>(null) }
    var showBulkSmsConfirmation by remember { mutableStateOf(false) }


    val subscriberMap = remember(subscribers) { subscribers.associateBy { it.id } }

    val filteredInvoices = remember(invoices, subscribers, searchQuery, statusFilter, dateWindowDays) {
        val q = searchQuery.trim()
        val cutoff = if (dateWindowDays > 0) System.currentTimeMillis() - dateWindowDays * 86_400_000L else 0L
        invoices
            .filter { inv ->
                val matchesStatus = statusFilter == "ALL" || inv.status == statusFilter
                val matchesDate = cutoff == 0L || inv.issuedAt >= cutoff
                val sub = subscriberMap[inv.subscriberId]
                val matchesSearch = q.isEmpty() ||
                    inv.invoiceNumber.contains(q, ignoreCase = true) ||
                    inv.billingPeriod.contains(q, ignoreCase = true) ||
                    (sub?.fullName?.contains(q, ignoreCase = true) == true) ||
                    (sub?.subscriberCode?.contains(q, ignoreCase = true) == true) ||
                    (sub?.phoneNumber?.contains(q, ignoreCase = true) == true)
                matchesStatus && matchesDate && matchesSearch
            }
            .sortedWith(compareByDescending<InvoiceEntity> { it.issuedAt }.thenByDescending { it.id })
    }

    fun dispatchBulkSms() {
        var sent = 0
        var failed = 0
        filteredInvoices.forEach { invoice ->
            val subscriber = subscribers.firstOrNull { it.id == invoice.subscriberId }
            if (subscriber == null) failed++ else {
                val error = InvoiceShareAndPrintService.sendInvoiceSms(context, invoice, subscriber, notificationPrefs.defaultCountryCode)
                if (error == null) sent++ else failed++
            }
        }
        smsSummary = "تم طلب إرسال $sent فاتورة، وتعذر إرسال $failed فاتورة."
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dispatchBulkSms() else smsSummary = "لم تمنح التطبيق إذن إرسال الرسائل SMS."
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ملفات المشتركين — اضغط على الاسم لعرض الحساب الكامل",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(subscribers, key = { it.id }) { subscriber ->
                FilterChip(
                    selected = selectedSubscriberForDetails?.id == subscriber.id,
                    onClick = { selectedSubscriberForDetails = subscriber },
                    label = { Text(subscriber.fullName, maxLines = 1) }
                )
            }
        }
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
                .testTag("invoices_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header_filters") {
                Column(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "إدارة الفواتير والحسابات (${filteredInvoices.size})",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("بحث برقم الفاتورة أو اسم/رقم/هاتف المشترك") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("invoices_search_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val statuses = listOf(
                            "ALL" to "الكل",
                            "UNPAID" to "غير مدفوعة",
                            "PARTIAL" to "جزئية",
                            "PAID" to "مدفوعة",
                            "CARRIED_OVER" to "مرحّلة"
                        )
                        items(statuses) { (key, label) ->
                            FilterChip(
                                selected = statusFilter == key,
                                onClick = { statusFilter = key },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "كل التواريخ", 7 to "آخر 7 أيام", 30 to "آخر 30 يوماً").forEach { (days, label) ->
                            FilterChip(
                                selected = dateWindowDays == days,
                                onClick = { dateWindowDays = days },
                                label = { Text(label) }
                            )
                        }
                    if (notificationPrefs.bulkInvoiceSmsEnabled) {
                        Button(onClick = { showBulkSmsConfirmation = true }, enabled = filteredInvoices.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp).testTag("bulk_invoice_sms_button")) {
                            Text("إرسال جميع الفواتير SMS (${filteredInvoices.size})")
                        }
                    }
                    smsSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }

            if (filteredInvoices.isEmpty()) {
                item(key = "empty_invoices") {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "لا توجد فواتير مطابقة",
                        description = "سجّل قراءة عداد من تبويب «القراءات» واضغط «حفظ وإصدار فاتورة» لإنشاء فاتورة تلقائياً بسعر المشترك وترحيل المتأخرات.",
                        modifier = Modifier.widthIn(max = 800.dp),
                        testTag = "invoices_empty_state"
                    )
                }
            } else {
                items(filteredInvoices, key = { it.id }) { inv ->
                    val sub = subscriberMap[inv.subscriberId]
                    val isPaid = inv.status == "PAID"
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .testTag("invoice_card_${inv.invoiceNumber}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
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
                                    Text(
                                        text = "${inv.invoiceNumber} — ${sub?.fullName ?: "مشترك #${inv.subscriberId}"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "الفترة: ${inv.billingPeriod} | الإصدار: ${dateFormat.format(Date(inv.issuedAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    color = if (isPaid) alasalColors.creditContainer else alasalColors.arrearsContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = InvoiceShareAndPrintService.translateStatus(inv.status),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isPaid) alasalColors.creditGreen else alasalColors.arrearsRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "استهلاك: ${inv.consumedKwh} × ${inv.unitPriceApplied} = ${inv.energyAmount} | متأخرات: ${inv.previousArrearsApplied} | خصم دائن: ${inv.creditDeducted}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "الإجمالي: ${inv.netPayableAmount}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "المدفوع: ${inv.paidAmount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = alasalColors.creditGreen
                                )
                                Text(
                                    text = "المتبقي: ${inv.remainingAmount}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (inv.remainingAmount > 0) alasalColors.arrearsRed else alasalColors.creditGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onSelectInvoice(inv.id) },
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("invoice_detail_btn_${inv.invoiceNumber}")
                                ) {
                                    Text("التفاصيل والسداد")
                                }
                                IconButton(
                                    onClick = { InvoiceShareAndPrintService.printInvoice(context, inv, sub) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Print, contentDescription = "طباعة الفاتورة")
                                }
                                IconButton(
                                    onClick = { InvoiceShareAndPrintService.shareInvoiceText(context, inv, sub) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = "مشاركة الفاتورة")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val warn = InvoiceShareAndPrintService.sendViaWhatsApp(
                                            context = context,
                                            invoice = inv,
                                            subscriber = sub,
                                            defaultCountryCode = notificationPrefs.defaultCountryCode
                                        )
                                        if (warn != null) {
                                            onFeedbackWarning(warn)
                                        }
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("invoice_whatsapp_btn_${inv.invoiceNumber}")
                                ) {
                                    Text("واتساب", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBulkSmsConfirmation) {
        AlertDialog(
            onDismissRequest = { showBulkSmsConfirmation = false },
            title = { Text("تأكيد إرسال الفواتير") },
            text = { Text("سيتم إرسال ${filteredInvoices.size} فاتورة SMS حسب الفلاتر المحددة. قد تتحول الرسالة إلى أكثر من جزء إذا تجاوزت حد SMS.") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkSmsConfirmation = false
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) dispatchBulkSms()
                    else smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                }) { Text("إرسال") }
            },
            dismissButton = { TextButton(onClick = { showBulkSmsConfirmation = false }) { Text("إلغاء") } }
        )
    }

    selectedSubscriberForDetails?.let { subscriber ->
        val subscriberInvoices = invoices.filter { it.subscriberId == subscriber.id }
            .sortedByDescending { it.issuedAt }
        val subscriberCollections = collectionsWithReceipts
            .filter { it.collection.subscriberId == subscriber.id }
        AlertDialog(
            onDismissRequest = { selectedSubscriberForDetails = null },
            title = { Text("ملف المشترك: ${subscriber.fullName}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("رقم المشترك: ${subscriber.subscriberCode}")
                    Text("الهاتف: ${subscriber.phoneNumber.ifBlank { "-" }}")
                    Text("العنوان: ${subscriber.address.ifBlank { "-" }}")
                    Text("رقم العداد: ${subscriber.meterNumber}")
                    Text("ختم العداد: ${subscriber.meterSealNumber.ifBlank { "-" }}")
                    Text("سعر الكيلوواط: ${subscriber.customKwhPrice}")
                    Text("آخر قراءة افتتاحية: ${subscriber.initialMeterReading}")
                    Text("المتأخرات الحالية: ${subscriber.currentArrearsAmount}")
                    Text("الرصيد الدائن: ${subscriber.currentCreditBalance}")
                    HorizontalDivider()
                    Text("الفواتير: ${subscriberInvoices.size}")
                    Text("إجمالي المتبقي: ${subscriberInvoices.sumOf { it.remainingAmount }}")
                    Text("عدد التحصيلات: ${subscriberCollections.size}")
                    Text("إجمالي المحصل: ${subscriberCollections.sumOf { it.collection.amountPaid }}")
                    subscriberInvoices.take(8).forEach { invoice ->
                        Text("${invoice.invoiceNumber} — ${invoice.billingPeriod} — المتبقي ${invoice.remainingAmount}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSubscriberForDetails = null }) { Text("إغلاق") }
            }
        )
    }

    if (selectedInvoiceDetail != null) {
        InvoiceDetailDialog(
            bundle = selectedInvoiceDetail,
            subscriber = subscriberMap[selectedInvoiceDetail.invoice.subscriberId],
            users = users,
            collectionsWithReceipts = collectionsWithReceipts,
            defaultCountryCode = notificationPrefs.defaultCountryCode,
            onSelectReceipt = onSelectReceipt,
            onDismiss = onDismissDetail,
            onRecordPayment = onRecordPayment,
            onRepriceInvoice = onRepriceInvoice,
            onDeleteInvoice = onDeleteInvoice
        )
    }
}

@Composable
private fun InvoiceDetailDialog(
    bundle: InvoiceDetailBundle,
    subscriber: SubscriberEntity?,
    users: List<UserEntity>,
    collectionsWithReceipts: List<CollectionWithReceipt>,
    defaultCountryCode: String = "967",
    onSelectReceipt: (ReceiptEntity) -> Unit,
    onDismiss: () -> Unit,
    onRecordPayment: (Long, Double, String, Long?, () -> Unit) -> Unit,
    onRepriceInvoice: (Long, Double, String, () -> Unit) -> Unit,
    onDeleteInvoice: (Long, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val inv = bundle.invoice
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }
    val receiptByCollectionId = remember(collectionsWithReceipts) {
        collectionsWithReceipts.associate { it.collection.id to it.receipt }
    }

    var showPaymentDialog by remember { mutableStateOf(false) }
    var showRepriceDialog by remember { mutableStateOf(false) }
    var dialogWhatsappWarning by remember(inv.id) { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تفاصيل ${inv.invoiceNumber}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                    }
                }

                Text(
                    text = "المشترك: ${subscriber?.fullName ?: "-"} (${subscriber?.subscriberCode ?: "-"})",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "القراءة: من ${inv.previousReading} إلى ${inv.currentReading} | الاستهلاك: ${inv.consumedKwh} ك.و.س",
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()

                Text("تفصيل الاستحقاق:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("• قيمة الاستهلاك الحالي (${inv.consumedKwh} × ${inv.unitPriceApplied}): ${inv.energyAmount}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "• المتأخرات السابقة المرحلة: ${inv.previousArrearsApplied}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inv.previousArrearsApplied > 0) alasalColors.arrearsRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• الرصيد الدائن المخصوم: ${inv.creditDeducted}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inv.creditDeducted > 0) alasalColors.creditGreen else MaterialTheme.colorScheme.onSurface
                )
                Text("• صافي الفاتورة: ${inv.netPayableAmount} | المدفوع: ${inv.paidAmount} | المتبقي: ${inv.remainingAmount}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                if (bundle.sourceArrearsInvoices.isNotEmpty()) {
                    Surface(
                        color = alasalColors.arrearsContainer.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                text = "الفواتير المكونة للمتأخرات المرحلة:",
                                style = MaterialTheme.typography.labelLarge,
                                color = alasalColors.arrearsRed
                            )
                            bundle.sourceArrearsInvoices.forEach { src ->
                                Text(
                                    text = "- ${src.invoiceNumber} (${src.billingPeriod}): متبقي مرحّل ${src.remainingAmount}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (inv.priceModifiedAt != null) {
                    Surface(
                        color = alasalColors.gold.copy(alpha = 0.16f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "تم تعديل سعر الفاتورة بتاريخ ${dateFormat.format(Date(inv.priceModifiedAt))} بواسطة ${inv.priceModifiedBy}: ${inv.priceModificationReason}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                    }
                }

                if (bundle.collections.isNotEmpty()) {
                    Text("التحصيلات والإيصالات المرتبطة (${bundle.collections.size}):", style = MaterialTheme.typography.titleSmall)
                    bundle.collections.forEach { col ->
                        val receipt = receiptByCollectionId[col.id]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${receipt?.receiptNumber ?: col.collectionReference}: ${col.amountPaid} (${col.collectorNameSnapshot})",
                                style = MaterialTheme.typography.bodySmall,
                                color = alasalColors.creditGreen,
                                modifier = Modifier.weight(1f)
                            )
                            if (receipt != null) {
                                TextButton(onClick = { onSelectReceipt(receipt) }) {
                                    Text("عرض الإيصال")
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (inv.status != "CARRIED_OVER" && inv.status != "PAID") {
                        Button(
                            onClick = { showPaymentDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("invoice_pay_button")
                        ) {
                            Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("تسجيل سداد")
                        }
                    }
                    if (inv.status != "CARRIED_OVER") {
                        OutlinedButton(
                            onClick = { showRepriceDialog = true },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("invoice_reprice_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("تعديل السعر")
                        }
                    }
                }

                if (dialogWhatsappWarning != null) {
                    Surface(
                        color = alasalColors.arrearsContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().testTag("invoice_detail_whatsapp_warning")
                    ) {
                        Text(
                            text = dialogWhatsappWarning!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = alasalColors.arrearsRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { InvoiceShareAndPrintService.printInvoice(context, inv, subscriber) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("طباعة")
                    }
                    OutlinedButton(
                        onClick = { InvoiceShareAndPrintService.shareInvoiceText(context, inv, subscriber) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("مشاركة")
                    }
                    Button(
                        onClick = {
                            dialogWhatsappWarning = InvoiceShareAndPrintService.sendViaWhatsApp(
                                context = context,
                                invoice = inv,
                                subscriber = subscriber,
                                defaultCountryCode = defaultCountryCode
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("invoice_detail_whatsapp_button")
                    ) {
                        Text("واتساب")
                    }
                }

                if (inv.paidAmount == 0.0 && inv.status != "CARRIED_OVER" && bundle.collections.isEmpty()) {
                    OutlinedButton(
                        onClick = { onDeleteInvoice(inv.id, onDismiss) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("invoice_delete_button")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = alasalColors.arrearsRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("حذف الفاتورة غير المحصلة", color = alasalColors.arrearsRed)
                    }
                }
            }
        }
    }

    if (showPaymentDialog) {
        var amountInput by remember { mutableStateOf(inv.remainingAmount.toString()) }
        var paymentMethod by remember { mutableStateOf("CASH") }
        val activeUsers = remember(users) { users.filter { it.isActive } }
        var selectedCollectorId by remember(activeUsers) { mutableStateOf(activeUsers.firstOrNull()?.id) }
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.90f).widthIn(max = 460.dp),
            title = { Text("تسجيل دفعة سداد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المتبقي الحالي: ${inv.remainingAmount} (يمكن السداد الجزئي أو الزائد للرصيد الدائن)")
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("المبلغ المدفوع *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH" to "نقدي", "BANK_TRANSFER" to "حوالة", "MOBILE_WALLET" to "محفظة").forEach { (code, label) ->
                            FilterChip(
                                selected = paymentMethod == code,
                                onClick = { paymentMethod = code },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (activeUsers.isNotEmpty()) {
                        Text("المحصّل:", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(activeUsers, key = { it.id }) { u ->
                                FilterChip(
                                    selected = selectedCollectorId == u.id,
                                    onClick = { selectedCollectorId = u.id },
                                    label = { Text(u.fullName) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountInput.toDoubleOrNull() ?: 0.0
                        onRecordPayment(inv.id, amt, paymentMethod, selectedCollectorId) { showPaymentDialog = false }
                    },
                    modifier = Modifier.testTag("payment_confirm_button")
                ) { Text("اعتماد السداد") }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) { Text("إلغاء") }
            }
        )
    }

    if (showRepriceDialog) {
        var newPriceInput by remember { mutableStateOf(inv.unitPriceApplied.toString()) }
        var reasonInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRepriceDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.90f).widthIn(max = 460.dp),
            title = { Text("تعديل سعر الفاتورة القديمة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("السعر الحالي: ${inv.unitPriceApplied} | المدفوع المحفوظ: ${inv.paidAmount}")
                    OutlinedTextField(
                        value = newPriceInput,
                        onValueChange = { newPriceInput = it },
                        label = { Text("سعر الكيلوواط الجديد *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reprice_new_price_input")
                    )
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("سبب التعديل (إلزامي) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reprice_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = newPriceInput.toDoubleOrNull() ?: -1.0
                        onRepriceInvoice(inv.id, price, reasonInput) { showRepriceDialog = false }
                    },
                    modifier = Modifier.testTag("reprice_confirm_button")
                ) { Text("حفظ وتحديث الفاتورة") }
            },
            dismissButton = {
                TextButton(onClick = { showRepriceDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
