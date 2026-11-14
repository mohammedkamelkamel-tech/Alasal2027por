package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.finance.DateRangePreset
import com.example.domain.finance.StationReportGenerator
import com.example.service.DocumentPdfAndThermalService
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CollectionsAndReceiptsScreen(
    invoices: List<InvoiceEntity>,
    subscribers: List<SubscriberEntity>,
    users: List<UserEntity>,
    collectionsWithReceipts: List<CollectionWithReceipt>,
    selectedReceipt: ReceiptEntity?,
    notificationPrefs: NotificationAndPrintPrefs,
    feedback: FeedbackMessage?,
    onDismissFeedback: () -> Unit,
    onNavigateBack: () -> Unit,
    onRecordCollection: (
        invoiceId: Long,
        amountPaid: Double,
        paymentMethod: String,
        notes: String,
        collectorUserId: Long?,
        onDone: () -> Unit
    ) -> Unit,
    onSelectReceipt: (ReceiptEntity) -> Unit,
    onDismissReceipt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alasalColors = LocalAlasalColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }
    val subMap = remember(subscribers) { subscribers.associateBy { it.id } }
    val activeInvoices = remember(invoices) {
        invoices.filter { it.carriedOverToInvoiceId == null && it.remainingAmount > 0.0 && it.status in listOf("UNPAID", "PARTIAL") }
    }
    val eligibleCollectors = remember(users) {
        users.filter { it.isActive }
    }

    var selectedInvoiceId by remember { mutableStateOf<Long?>(null) }
    var selectedCollectorId by remember { mutableStateOf<Long?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var notesInput by remember { mutableStateOf("") }

    // Search & Filter states
    var searchQuery by remember { mutableStateOf("") }
    var filterSubscriberId by remember { mutableStateOf<Long?>(null) }
    var filterCollectorId by remember { mutableStateOf<Long?>(null) }
    var datePreset by remember { mutableStateOf(DateRangePreset.ALL) }

    LaunchedEffect(activeInvoices) {
        if (selectedInvoiceId == null && activeInvoices.isNotEmpty()) {
            selectedInvoiceId = activeInvoices.first().id
            amountInput = activeInvoices.first().remainingAmount.toString()
        } else if (selectedInvoiceId != null && activeInvoices.none { it.id == selectedInvoiceId }) {
            selectedInvoiceId = activeInvoices.firstOrNull()?.id
            amountInput = activeInvoices.firstOrNull()?.remainingAmount?.toString() ?: ""
        }
    }

    LaunchedEffect(eligibleCollectors) {
        if (selectedCollectorId == null && eligibleCollectors.isNotEmpty()) {
            selectedCollectorId = eligibleCollectors.first().id
        }
    }

    val selectedInvoice = activeInvoices.firstOrNull { it.id == selectedInvoiceId }

    val filteredCollections = remember(
        collectionsWithReceipts,
        searchQuery,
        filterSubscriberId,
        filterCollectorId,
        datePreset
    ) {
        val bounds = StationReportGenerator.resolveTimeBounds(datePreset)
        val q = searchQuery.trim()
        collectionsWithReceipts.filter { item ->
            val col = item.collection
            val rcp = item.receipt
            val sub = subMap[col.subscriberId]
            val matchesSub = filterSubscriberId == null || col.subscriberId == filterSubscriberId
            val matchesCollector = filterCollectorId == null || col.collectedByUserId == filterCollectorId
            val matchesDate = bounds == null || col.collectedAt in bounds.first..bounds.second
            val matchesSearch = q.isEmpty() ||
                col.collectionReference.contains(q, ignoreCase = true) ||
                (rcp?.receiptNumber?.contains(q, ignoreCase = true) == true) ||
                (rcp?.invoiceNumberSnapshot?.contains(q, ignoreCase = true) == true) ||
                col.collectorNameSnapshot.contains(q, ignoreCase = true) ||
                (sub?.fullName?.contains(q, ignoreCase = true) == true) ||
                (sub?.subscriberCode?.contains(q, ignoreCase = true) == true)
            matchesSub && matchesCollector && matchesDate && matchesSearch
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
                .testTag("collections_screen"),
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
                        Text(
                            text = "التحصيل وإيصالات السداد",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "تحصيل كامل أو جزئي أو متعدد على نفس الفاتورة مع ترقيم تسلسلي محفوظ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "collection_form") {
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
                        Text("تسجيل عملية تحصيل جديدة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (activeInvoices.isEmpty()) {
                            Text(
                                text = "لا توجد فواتير نشطة متبقية للتحصيل حالياً.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("اختر الفاتورة المستحقة:", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(activeInvoices, key = { it.id }) { inv ->
                                    val subName = subMap[inv.subscriberId]?.fullName ?: "-"
                                    FilterChip(
                                        selected = inv.id == selectedInvoiceId,
                                        onClick = {
                                            selectedInvoiceId = inv.id
                                            amountInput = inv.remainingAmount.toString()
                                        },
                                        label = { Text("${inv.invoiceNumber} ($subName) • متبقي ${inv.remainingAmount}") },
                                        modifier = Modifier.testTag("collection_inv_chip_${inv.invoiceNumber}")
                                    )
                                }
                            }

                            if (selectedInvoice != null) {
                                Surface(
                                    color = alasalColors.gold.copy(alpha = 0.14f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "المتبقي الحالي: ${selectedInvoice.remainingAmount} | المدفوع سابقاً: ${selectedInvoice.paidAmount}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TextButton(onClick = { amountInput = selectedInvoice.remainingAmount.toString() }) {
                                            Text("سداد كامل")
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = amountInput,
                                onValueChange = { amountInput = it },
                                label = { Text("المبلغ المحصل (يدعم الجزئي والكامل والزائد) *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("collection_amount_input")
                            )

                            Text("طريقة التحصيل:", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(
                                    listOf(
                                        "CASH" to "نقدي",
                                        "BANK_TRANSFER" to "تحويل بنكي",
                                        "MOBILE_WALLET" to "محفظة إلكترونية",
                                        "CHECK" to "شيك"
                                    )
                                ) { (code, label) ->
                                    FilterChip(
                                        selected = paymentMethod == code,
                                        onClick = { paymentMethod = code },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("collection_method_$code")
                                    )
                                }
                            }

                            if (eligibleCollectors.isNotEmpty()) {
                                Text("اسم المحصل المنفذ:", style = MaterialTheme.typography.labelLarge)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(eligibleCollectors, key = { it.id }) { user ->
                                        FilterChip(
                                            selected = selectedCollectorId == user.id,
                                            onClick = { selectedCollectorId = user.id },
                                            label = { Text(user.fullName) },
                                            modifier = Modifier.testTag("collection_collector_${user.username}")
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notesInput,
                                onValueChange = { notesInput = it },
                                label = { Text("ملاحظات التحصيل (اختياري)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    val inv = selectedInvoice ?: return@Button
                                    val amt = amountInput.toDoubleOrNull() ?: 0.0
                                    onRecordCollection(
                                        inv.id,
                                        amt,
                                        paymentMethod,
                                        notesInput,
                                        selectedCollectorId
                                    ) {
                                        notesInput = ""
                                    }
                                },
                                enabled = selectedInvoice != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("collection_submit_button")
                            ) {
                                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("اعتماد التحصيل وإصدار الإيصال المتسلسل")
                            }
                        }
                    }
                }
            }

            item(key = "filters_header") {
                Column(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "سجل التحصيلات والإيصالات (${filteredCollections.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("بحث برقم الإيصال، الفاتورة، المشترك، أو المحصل") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("collections_search_input")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(DateRangePreset.ALL, DateRangePreset.TODAY, DateRangePreset.THIS_MONTH)) { preset ->
                            FilterChip(
                                selected = datePreset == preset,
                                onClick = { datePreset = preset },
                                label = { Text(preset.titleAr) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterCollectorId == null,
                                onClick = { filterCollectorId = null },
                                label = { Text("كل المحصلين") }
                            )
                        }
                        items(eligibleCollectors, key = { "fc_${it.id}" }) { user ->
                            FilterChip(
                                selected = filterCollectorId == user.id,
                                onClick = { filterCollectorId = user.id },
                                label = { Text(user.fullName) }
                            )
                        }
                    }
                    if (subscribers.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = filterSubscriberId == null,
                                    onClick = { filterSubscriberId = null },
                                    label = { Text("كل المشتركين") }
                                )
                            }
                            items(subscribers, key = { "fs_${it.id}" }) { sub ->
                                FilterChip(
                                    selected = filterSubscriberId == sub.id,
                                    onClick = { filterSubscriberId = sub.id },
                                    label = { Text(sub.fullName) }
                                )
                            }
                        }
                    }
                }
            }

            if (filteredCollections.isEmpty()) {
                item(key = "empty_collections") {
                    EmptyStateCard(
                        icon = Icons.Filled.Receipt,
                        title = "لا توجد تحصيلات مطابقة",
                        description = "سجّل دفعة تحصيل على أي فاتورة نشطة لإصدار إيصال متسلسل فوراً.",
                        modifier = Modifier.widthIn(max = 800.dp)
                    )
                }
            } else {
                items(filteredCollections, key = { it.collection.id }) { item ->
                    val col = item.collection
                    val rcp = item.receipt
                    val sub = subMap[col.subscriberId]
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .testTag("collection_card_${rcp?.receiptNumber ?: col.collectionReference}"),
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
                                    text = "${rcp?.receiptNumber ?: col.collectionReference} — ${sub?.fullName ?: rcp?.subscriberNameSnapshot ?: "-"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = alasalColors.creditContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "+${col.amountPaid}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = alasalColors.creditGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "فاتورة: ${rcp?.invoiceNumberSnapshot ?: "-"} | المحصل: ${col.collectorNameSnapshot} | ${StationReportGenerator.translatePaymentMethod(col.paymentMethod)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "السابق: ${col.previousBalanceSnapshot} ← المتبقي بعدها: ${col.remainingAfterSnapshot} | ${dateFormat.format(Date(col.collectedAt))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (rcp != null) {
                                OutlinedButton(
                                    onClick = { onSelectReceipt(rcp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_receipt_btn_${rcp.receiptNumber}")
                                ) {
                                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("عرض وطباعة ومشاركة الإيصال")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedReceipt != null) {
        ReceiptDetailDialog(
            receipt = selectedReceipt,
            subscriberPhone = subMap[selectedReceipt.subscriberId]?.phoneNumber,
            prefs = notificationPrefs,
            onDismiss = onDismissReceipt
        )
    }
}

@Composable
fun ReceiptDetailDialog(
    receipt: ReceiptEntity,
    subscriberPhone: String?,
    prefs: NotificationAndPrintPrefs,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }
    var thermalWidthMm by remember(prefs.thermalPaperWidthMm) { mutableIntStateOf(prefs.thermalPaperWidthMm) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var actionFeedbackIsError by remember { mutableStateOf(false) }

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
            Box {
                Image(
                    painter = painterResource(id = R.drawable.img_alasal_watermark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(220.dp)
                        .align(Alignment.Center)
                        .alpha(0.10f)
                )
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_alasal_logo_transparent),
                                contentDescription = "شعار محطة العسل",
                                modifier = Modifier.size(44.dp)
                            )
                            Column {
                                Text(
                                    text = receipt.stationNameSnapshot.replace("Alasal2027", "").replace("2027", "").trim().ifBlank { "محطة العسل" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "إيصال تحصيل: ${receipt.receiptNumber}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = alasalColors.gold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق")
                        }
                    }

                    HorizontalDivider()
                    Text("المشترك: ${receipt.subscriberNameSnapshot} (${receipt.subscriberCodeSnapshot})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("رقم الفاتورة: ${receipt.invoiceNumberSnapshot}", style = MaterialTheme.typography.bodyMedium)
                    Text("طريقة التحصيل: ${StationReportGenerator.translatePaymentMethod(receipt.paymentMethod)}", style = MaterialTheme.typography.bodyMedium)
                    Text("اسم المحصل: ${receipt.collectorNameSnapshot}", style = MaterialTheme.typography.bodyMedium)
                    Text("التاريخ والوقت: ${dateFormat.format(Date(receipt.issuedAt))}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المبلغ السابق:", style = MaterialTheme.typography.bodyMedium)
                        Text("${receipt.previousBalance}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المبلغ المحصل:", style = MaterialTheme.typography.titleSmall, color = alasalColors.creditGreen)
                        Text("${receipt.amount}", style = MaterialTheme.typography.titleMedium, color = alasalColors.creditGreen, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المتبقي بعد التحصيل:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${receipt.balanceAfterReceipt}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (receipt.balanceAfterReceipt > 0) alasalColors.arrearsRed else alasalColors.creditGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (receipt.creditAfterReceipt > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الرصيد الدائن:", style = MaterialTheme.typography.bodyMedium, color = alasalColors.creditGreen)
                            Text("${receipt.creditAfterReceipt}", style = MaterialTheme.typography.bodyMedium, color = alasalColors.creditGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "${prefs.stationAddress} | هاتف: ${receipt.stationPhoneSnapshot.ifEmpty { prefs.stationPhone }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (actionFeedback != null) {
                        Text(
                            text = actionFeedback!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (actionFeedbackIsError) alasalColors.arrearsRed else alasalColors.creditGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("receipt_action_feedback")
                        )
                    }

                    Text("مقاس الطباعة الحرارية:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = thermalWidthMm == 58,
                            onClick = { thermalWidthMm = 58 },
                            label = { Text("58mm") },
                            modifier = Modifier.testTag("thermal_width_58")
                        )
                        FilterChip(
                            selected = thermalWidthMm == 80,
                            onClick = { thermalWidthMm = 80 },
                            label = { Text("80mm") },
                            modifier = Modifier.testTag("thermal_width_80")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = DocumentPdfAndThermalService.generateReceiptA4Pdf(
                                    context = context,
                                    receipt = receipt,
                                    stationAddress = prefs.stationAddress
                                )
                                DocumentPdfAndThermalService.printPdfFile(context, file, "Alasal_A4_${receipt.receiptNumber}")
                                actionFeedbackIsError = false
                                actionFeedback = "تم تجهيز PDF A4 للطباعة (${file.name})"
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("receipt_print_a4_button")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("طباعة A4", maxLines = 1)
                        }
                        Button(
                            onClick = {
                                val file = DocumentPdfAndThermalService.generateThermalReceiptPdf(
                                    context = context,
                                    receipt = receipt,
                                    paperWidthMm = thermalWidthMm,
                                    stationAddress = prefs.stationAddress
                                )
                                DocumentPdfAndThermalService.printPdfFile(
                                    context = context,
                                    pdfFile = file,
                                    jobName = "Alasal_Thermal_${thermalWidthMm}mm_${receipt.receiptNumber}",
                                    thermalWidthMm = thermalWidthMm
                                )
                                actionFeedbackIsError = false
                                actionFeedback = "تم تجهيز الطباعة الحرارية ${thermalWidthMm}mm"
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("receipt_print_thermal_button")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("حرارية $thermalWidthMm", maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pdf = DocumentPdfAndThermalService.generateReceiptA4Pdf(
                                    context = context,
                                    receipt = receipt,
                                    stationAddress = prefs.stationAddress
                                )
                                DocumentPdfAndThermalService.shareFile(context, pdf, "application/pdf", "مشاركة الإيصال PDF")
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("receipt_share_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("PDF", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                val img = DocumentPdfAndThermalService.generateReceiptPngImage(
                                    context = context,
                                    receipt = receipt,
                                    stationAddress = prefs.stationAddress
                                )
                                DocumentPdfAndThermalService.shareFile(context, img, "image/png", "مشاركة الإيصال كصورة")
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("receipt_share_image_button")
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("صورة", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                val warn = DocumentPdfAndThermalService.shareReceiptViaWhatsApp(
                                    context = context,
                                    receipt = receipt,
                                    subscriberPhone = subscriberPhone,
                                    defaultCountryCode = prefs.defaultCountryCode
                                )
                                if (warn != null) {
                                    actionFeedbackIsError = true
                                    actionFeedback = warn
                                } else {
                                    actionFeedbackIsError = false
                                    actionFeedback = "تم فتح واتساب لإرسال الإيصال (${receipt.receiptNumber})"
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("receipt_share_whatsapp_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("واتساب", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
