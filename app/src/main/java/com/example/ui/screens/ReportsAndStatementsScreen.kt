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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
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
import com.example.data.local.entity.ArrearsEntity
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.finance.DateRangePreset
import com.example.domain.finance.FieldDebtFilter
import com.example.domain.finance.ReportCategory
import com.example.domain.finance.ReportFilter
import com.example.domain.finance.StationReportGenerator
import com.example.service.DocumentPdfAndThermalService
import com.example.service.InvoiceShareAndPrintService
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.LocalAlasalColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsAndStatementsScreen(
    subscribers: List<SubscriberEntity>,
    invoices: List<InvoiceEntity>,
    collectionsWithReceipts: List<CollectionWithReceipt>,
    readings: List<MeterReadingEntity>,
    arrearsRecords: List<ArrearsEntity>,
    creditTransactions: List<CreditBalanceEntity>,
    users: List<UserEntity>,
    notificationPrefs: NotificationAndPrintPrefs,
    onNavigateBack: () -> Unit,
    initialTabIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }
    val shortDateFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.forLanguageTag("ar")) }

    var activeTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex.coerceIn(0, 1)) }
    var areaFilterInput by remember { mutableStateOf("") }
    var fieldDebtFilter by remember { mutableStateOf(FieldDebtFilter.ARREARS_ONLY) }
    var fieldOnlyActive by remember { mutableStateOf(true) }
    var fieldExportStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var selectedCategory by remember { mutableStateOf(ReportCategory.ALL_INVOICES) }
    var selectedSubscriberId by remember { mutableStateOf<Long?>(null) }
    var selectedCollectorId by remember { mutableStateOf<Long?>(null) }
    var selectedDatePreset by remember { mutableStateOf(DateRangePreset.ALL) }
    var customStartDaysAgo by remember { mutableIntStateOf(30) }
    var customEndDaysAgo by remember { mutableIntStateOf(0) }
    var pdfActionStatus by remember { mutableStateOf<String?>(null) }
    var pdfActionIsError by remember { mutableStateOf(false) }

    val baseNow = remember { System.currentTimeMillis() }
    val customStartMillis = baseNow - customStartDaysAgo * 86_400_000L
    val customEndMillis = baseNow - customEndDaysAgo * 86_400_000L

    val filter = remember(
        selectedCategory,
        selectedSubscriberId,
        selectedCollectorId,
        selectedDatePreset,
        customStartMillis,
        customEndMillis
    ) {
        ReportFilter(
            category = selectedCategory,
            subscriberId = selectedSubscriberId,
            collectorUserId = selectedCollectorId,
            datePreset = selectedDatePreset,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis
        )
    }

    val generatedReport = remember(
        filter,
        subscribers,
        invoices,
        collectionsWithReceipts,
        readings,
        arrearsRecords,
        creditTransactions,
        users
    ) {
        StationReportGenerator.generateReport(
            filter = filter,
            subscribers = subscribers,
            invoices = invoices,
            collectionsWithReceipts = collectionsWithReceipts,
            readings = readings,
            arrears = arrearsRecords,
            creditTransactions = creditTransactions,
            users = users,
            nowMillis = baseNow
        )
    }

    val availableAreas = remember(subscribers) {
        subscribers.map { it.address.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val fieldItems = remember(subscribers, readings, areaFilterInput, fieldDebtFilter, fieldOnlyActive) {
        StationReportGenerator.buildFieldCollectionSheet(
            subscribers = subscribers,
            readings = readings,
            areaFilter = areaFilterInput,
            debtFilter = fieldDebtFilter,
            onlyActiveSubscribers = fieldOnlyActive
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Column {
                        Text(
                            text = "التقارير وكشوف الحساب والتحصيل الميداني",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "تقارير مالية، كشوف حساب مع واتساب، وكشوف نزول ميداني PDF و Excel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        label = { Text("التقارير وكشف الحساب") },
                        leadingIcon = { Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("tab_reports_financial")
                    )
                    FilterChip(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        label = { Text("كشف النزول الميداني للمحصلين") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("tab_field_collection")
                    )
                }
            }
        }

        if (activeTab == 0) {

        item(key = "filters") {
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
                    Text("نوع التقرير:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    LazyRow(
                        modifier = Modifier.testTag("reports_categories_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ReportCategory.entries) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.titleAr) },
                                modifier = Modifier.testTag("report_cat_${category.name}")
                            )
                        }
                    }

                    Text("الفترة الزمنية:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(DateRangePreset.entries) { preset ->
                            FilterChip(
                                selected = selectedDatePreset == preset,
                                onClick = { selectedDatePreset = preset },
                                label = { Text(preset.titleAr) },
                                modifier = Modifier.testTag("report_date_${preset.name}")
                            )
                        }
                    }

                    if (selectedDatePreset == DateRangePreset.CUSTOM_RANGE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "من: ${shortDateFormat.format(Date(customStartMillis))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { customStartDaysAgo += 7 }) { Text("-7 أيام") }
                            Text(
                                text = "إلى: ${shortDateFormat.format(Date(customEndMillis))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { customEndDaysAgo = (customEndDaysAgo - 1).coerceAtLeast(0) }) { Text("اليوم") }
                        }
                    }

                    if (subscribers.isNotEmpty()) {
                        Text("تصفية حسب المشترك:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedSubscriberId == null,
                                    onClick = { selectedSubscriberId = null },
                                    label = { Text("الكل") }
                                )
                            }
                            items(subscribers, key = { "rep_sub_${it.id}" }) { sub ->
                                FilterChip(
                                    selected = selectedSubscriberId == sub.id,
                                    onClick = { selectedSubscriberId = sub.id },
                                    label = { Text(sub.fullName) },
                                    modifier = Modifier.testTag("report_sub_${sub.subscriberCode}")
                                )
                            }
                        }
                    }

                    if (users.isNotEmpty()) {
                        Text("تصفية حسب المحصل:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCollectorId == null,
                                    onClick = { selectedCollectorId = null },
                                    label = { Text("كل المحصلين") }
                                )
                            }
                            items(users, key = { "rep_usr_${it.id}" }) { user ->
                                FilterChip(
                                    selected = selectedCollectorId == user.id,
                                    onClick = { selectedCollectorId = user.id },
                                    label = { Text(user.fullName) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = DocumentPdfAndThermalService.generateReportA4Pdf(
                                    context = context,
                                    report = generatedReport,
                                    stationAddress = notificationPrefs.stationAddress,
                                    stationPhone = notificationPrefs.stationPhone
                                )
                                DocumentPdfAndThermalService.shareFile(
                                    context = context,
                                    file = file,
                                    mimeType = "application/pdf",
                                    chooserTitle = "مشاركة التقرير PDF"
                                )
                                pdfActionIsError = false
                                pdfActionStatus = "تم تصدير ${file.name} (${generatedReport.uiRows.size} سجل)"
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("report_export_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("تصدير ومشاركة PDF", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                val file = DocumentPdfAndThermalService.generateReportA4Pdf(
                                    context = context,
                                    report = generatedReport,
                                    stationAddress = notificationPrefs.stationAddress,
                                    stationPhone = notificationPrefs.stationPhone
                                )
                                DocumentPdfAndThermalService.printPdfFile(context, file, generatedReport.title)
                                pdfActionIsError = false
                                pdfActionStatus = "تم إرسال التقرير للطباعة A4"
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("report_print_pdf_button")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("طباعة A4", maxLines = 1)
                        }
                    }

                    if (selectedCategory == ReportCategory.SUBSCRIBER_STATEMENT) {
                        val targetSub = selectedSubscriberId?.let { id -> subscribers.firstOrNull { it.id == id } }
                            ?: subscribers.firstOrNull()
                        if (targetSub != null) {
                            Button(
                                onClick = {
                                    val statement = StationReportGenerator.buildSubscriberStatement(
                                        subscriber = targetSub,
                                        invoices = invoices,
                                        collectionsWithReceipts = collectionsWithReceipts,
                                        readings = readings,
                                        creditTransactions = creditTransactions
                                    )
                                    val warn = InvoiceShareAndPrintService.sendStatementViaWhatsApp(
                                        context = context,
                                        statement = statement,
                                        defaultCountryCode = notificationPrefs.defaultCountryCode
                                    )
                                    if (warn != null) {
                                        pdfActionIsError = true
                                        pdfActionStatus = warn
                                    } else {
                                        pdfActionIsError = false
                                        pdfActionStatus = "تم فتح واتساب لإرسال كشف حساب ${targetSub.fullName}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("statement_whatsapp_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("إرسال كشف الحساب عبر واتساب (${targetSub.fullName})")
                            }
                        }
                    }

                    if (pdfActionStatus != null) {
                        Text(
                            text = pdfActionStatus!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pdfActionIsError) alasalColors.arrearsRed else alasalColors.creditGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item(key = "summary_card") {
            Card(
                modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth().testTag("report_summary_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = alasalColors.gold.copy(alpha = 0.13f)),
                border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(generatedReport.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(generatedReport.filterSummary, style = MaterialTheme.typography.bodySmall)
                    generatedReport.summaryTotals.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = value,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    label.contains("دائن") -> alasalColors.creditGreen
                                    label.contains("متأخر") -> alasalColors.arrearsRed
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }

        if (generatedReport.uiRows.isEmpty()) {
            item(key = "empty_report") {
                EmptyStateCard(
                    icon = Icons.Filled.Assessment,
                    title = "لا توجد بيانات ضمن هذا النطاق",
                    description = "جرّب تغيير الفلتر الزمني أو اختيار مشترك آخر.",
                    modifier = Modifier.widthIn(max = 800.dp)
                )
            }
        } else {
            items(generatedReport.uiRows, key = { it.idKey }) { row ->
                Card(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, alasalColors.cardBorder.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.primaryTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = when {
                                    row.isCreditAccent -> alasalColors.creditContainer
                                    row.isArrearsAccent -> alasalColors.arrearsContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${row.amountLabel} | ${row.statusLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        row.isCreditAccent -> alasalColors.creditGreen
                                        row.isArrearsAccent -> alasalColors.arrearsRed
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "${row.secondaryDetail} • ${dateFormat.format(Date(row.dateMillis))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        } else {
            item(key = "field_filters_card") {
                Card(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .testTag("field_collection_section"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "كشف النزول الميداني للمحصلين (PDF / Excel)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = areaFilterInput,
                            onValueChange = { areaFilterInput = it },
                            label = { Text("تصفية بالمنطقة أو العنوان أو اسم/رقم المشترك") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("field_area_filter_input")
                        )

                        if (availableAreas.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    FilterChip(
                                        selected = areaFilterInput.isBlank(),
                                        onClick = { areaFilterInput = "" },
                                        label = { Text("جميع المناطق") }
                                    )
                                }
                                items(availableAreas) { area ->
                                    FilterChip(
                                        selected = areaFilterInput.equals(area, ignoreCase = true),
                                        onClick = { areaFilterInput = area },
                                        label = { Text(area) }
                                    )
                                }
                            }
                        }

                        Text("تصفية حسب الحالة المالية:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(FieldDebtFilter.entries) { df ->
                                FilterChip(
                                    selected = fieldDebtFilter == df,
                                    onClick = { fieldDebtFilter = df },
                                    label = { Text(df.titleAr) },
                                    modifier = Modifier.testTag("field_debt_filter_${df.name}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("المشتركين النشطين فقط", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = fieldOnlyActive, onCheckedChange = { fieldOnlyActive = it })
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val pdf = DocumentPdfAndThermalService.generateFieldCollectionA4Pdf(
                                        context = context,
                                        items = fieldItems,
                                        areaFilterLabel = areaFilterInput.ifBlank { "جميع المناطق" },
                                        debtFilterLabel = fieldDebtFilter.titleAr,
                                        stationAddress = notificationPrefs.stationAddress,
                                        stationPhone = notificationPrefs.stationPhone
                                    )
                                    DocumentPdfAndThermalService.shareFile(
                                        context = context,
                                        file = pdf,
                                        mimeType = "application/pdf",
                                        chooserTitle = "مشاركة كشف النزول الميداني PDF"
                                    )
                                    fieldExportStatus = true to "تم تصدير كشف النزول A4 (${pdf.name} — ${fieldItems.size} مشترك)"
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("field_export_pdf_button")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("تصدير PDF", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    val pdf = DocumentPdfAndThermalService.generateFieldCollectionA4Pdf(
                                        context = context,
                                        items = fieldItems,
                                        areaFilterLabel = areaFilterInput.ifBlank { "جميع المناطق" },
                                        debtFilterLabel = fieldDebtFilter.titleAr,
                                        stationAddress = notificationPrefs.stationAddress,
                                        stationPhone = notificationPrefs.stationPhone
                                    )
                                    DocumentPdfAndThermalService.printPdfFile(context, pdf, "Alasal_FieldCollection")
                                    fieldExportStatus = true to "تم إرسال كشف النزول للطباعة المباشرة A4"
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("field_print_pdf_button")
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("طباعة", maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val csv = DocumentPdfAndThermalService.generateFieldCollectionCsv(
                                    context = context,
                                    items = fieldItems,
                                    areaFilterLabel = areaFilterInput.ifBlank { "جميع المناطق" }
                                )
                                DocumentPdfAndThermalService.shareFile(
                                    context = context,
                                    file = csv,
                                    mimeType = "text/csv",
                                    chooserTitle = "تصدير كشف النزول Excel (CSV)"
                                )
                                fieldExportStatus = true to "تم تصدير ملف Excel UTF-8 BOM (${csv.name} — ${fieldItems.size} صف)"
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("field_export_csv_button")
                        ) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("تصدير جدول Excel (CSV)")
                        }

                        if (fieldExportStatus != null) {
                            val (ok, msg) = fieldExportStatus!!
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ok) alasalColors.creditGreen else alasalColors.arrearsRed,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("field_export_status")
                            )
                        }
                    }
                }
            }

            item(key = "field_summary_card") {
                val totalDue = fieldItems.sumOf { it.currentArrears }
                Card(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth().testTag("field_summary_card"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = alasalColors.gold.copy(alpha = 0.13f)),
                    border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ملخص كشف النزول الميداني", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("عدد المشتركين المطابقين: ${fieldItems.size}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "المستحق: $totalDue",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (totalDue > 0.0) alasalColors.arrearsRed else alasalColors.creditGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (fieldItems.isEmpty()) {
                item(key = "field_empty") {
                    EmptyStateCard(
                        icon = Icons.Filled.LocationOn,
                        title = "لا توجد أسماء مطابقة لكشف النزول",
                        description = "جرّب تغيير المنطقة أو اختيار «جميع المشتركين» لعرض كل الأرصدة.",
                        modifier = Modifier.widthIn(max = 800.dp)
                    )
                }
            } else {
                items(fieldItems, key = { "fld_${it.subscriberId}" }) { item ->
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .testTag("field_item_${item.subscriberCode}"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.subscriberCode} — ${item.fullName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = if (item.currentArrears > 0) alasalColors.arrearsContainer else alasalColors.creditContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "المستحق: ${item.currentArrears}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.currentArrears > 0) alasalColors.arrearsRed else alasalColors.creditGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "المنطقة: ${item.address} | الهاتف: ${item.phoneNumber} | القراءة السابقة: ${item.previousMeterReading}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "خانة التدوين الميداني: المبلغ المحصل: _________ | التوقيع: _________",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
