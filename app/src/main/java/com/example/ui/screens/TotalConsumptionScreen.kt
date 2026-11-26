package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.domain.finance.ConsumptionSummaryCalculator
import com.example.domain.finance.FinancialCalculator
import com.example.domain.finance.HalfCycleSummary
import com.example.domain.finance.MonthlyConsumptionSummary
import com.example.service.DocumentPdfAndThermalService
import com.example.service.NotificationAndPrintPrefs
import com.example.ui.theme.LocalAlasalColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TotalConsumptionScreen(
    subscribers: List<SubscriberEntity>,
    readings: List<MeterReadingEntity>,
    invoices: List<InvoiceEntity>,
    notificationPrefs: NotificationAndPrintPrefs,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alasalColors = LocalAlasalColors.current
    val arabicLocale = remember { Locale.forLanguageTag("ar") }
    val numberFormat = remember {
        NumberFormat.getNumberInstance(arabicLocale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd", arabicLocale) }

    val availableYears = remember(readings) {
        ConsumptionSummaryCalculator.extractAvailableYears(readings)
    }
    val defaultYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var selectedYear by rememberSaveable { mutableIntStateOf(defaultYear) }
    var selectedMonthNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    var pdfActionMessage by remember { mutableStateOf<String?>(null) }

    val invoicedReadingIds = remember(invoices) {
        invoices.mapNotNull { it.meterReadingId }.toSet()
    }

    val yearMonthlySummaries = remember(selectedYear, readings, subscribers, invoicedReadingIds) {
        ConsumptionSummaryCalculator.buildYearMonthlySummaries(
            year = selectedYear,
            readings = readings,
            subscribers = subscribers,
            invoicedReadingIds = invoicedReadingIds
        )
    }

    val selectedMonthSummary: MonthlyConsumptionSummary? = remember(selectedMonthNumber, yearMonthlySummaries) {
        selectedMonthNumber?.let { m -> yearMonthlySummaries.firstOrNull { it.month == m } }
    }

    val annualTotalKwh = remember(yearMonthlySummaries) {
        FinancialCalculator.roundCurrency(yearMonthlySummaries.sumOf { it.totalMonthlyKwh })
    }
    val annualTotalReadings = remember(yearMonthlySummaries) {
        yearMonthlySummaries.sumOf { it.totalReadingsCount }
    }

    // Intercept system back when viewing a specific month detail
    BackHandler(enabled = selectedMonthSummary != null) {
        selectedMonthNumber = null
        pdfActionMessage = null
    }

    if (selectedMonthSummary != null) {
        MonthlyConsumptionDetailContent(
            summary = selectedMonthSummary,
            numberFormat = numberFormat,
            dateFormat = dateFormat,
            pdfActionMessage = pdfActionMessage,
            onBackToMonths = {
                selectedMonthNumber = null
                pdfActionMessage = null
            },
            onExportPdf = {
                runCatching {
                    val pdfFile = DocumentPdfAndThermalService.generateMonthlyConsumptionA4Pdf(
                        context = context,
                        summary = selectedMonthSummary,
                        stationAddress = notificationPrefs.stationAddress,
                        stationPhone = notificationPrefs.stationPhone
                    )
                    DocumentPdfAndThermalService.shareFile(
                        context = context,
                        file = pdfFile,
                        mimeType = "application/pdf",
                        chooserTitle = "مشاركة تقرير إجمالي الاستهلاك — ${selectedMonthSummary.monthTitleAr} ${selectedMonthSummary.year}"
                    )
                    pdfActionMessage = "تم إنشاء ومشاركة ملف PDF لتقرير ${selectedMonthSummary.monthTitleAr} ${selectedMonthSummary.year} بنجاح"
                }.onFailure { err ->
                    pdfActionMessage = "تعذر تصدير ملف PDF: ${err.message}"
                }
            },
            onPrintPdf = {
                runCatching {
                    val pdfFile = DocumentPdfAndThermalService.generateMonthlyConsumptionA4Pdf(
                        context = context,
                        summary = selectedMonthSummary,
                        stationAddress = notificationPrefs.stationAddress,
                        stationPhone = notificationPrefs.stationPhone
                    )
                    DocumentPdfAndThermalService.printPdfFile(
                        context = context,
                        pdfFile = pdfFile,
                        jobName = "Alasal2027_Consumption_${selectedMonthSummary.periodKey}"
                    )
                    pdfActionMessage = "تم إرسال تقرير ${selectedMonthSummary.monthTitleAr} ${selectedMonthSummary.year} إلى الطباعة"
                }.onFailure { err ->
                    pdfActionMessage = "تعذر إرسال التقرير للطباعة: ${err.message}"
                }
            },
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("total_consumption_screen"),
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
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("consumption_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.title_total_consumption),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إحصاء الاستهلاك الشهري ونصف الشهري من قراءات العدادات الفعلية",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Year switcher + Annual Total Overview Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("consumption_year_selector_card"),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = alasalColors.navyBrand),
                        border = BorderStroke(1.2.dp, alasalColors.gold.copy(alpha = 0.7f))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        tint = alasalColors.gold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "سنة الاستهلاك: $selectedYear",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("consumption_selected_year_label")
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { selectedYear -= 1 },
                                        modifier = Modifier.testTag("consumption_prev_year_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = "السنة السابقة",
                                            tint = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = { selectedYear += 1 },
                                        modifier = Modifier.testTag("consumption_next_year_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ChevronLeft,
                                            contentDescription = "السنة التالية",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }

                            LazyRow(
                                modifier = Modifier.testTag("consumption_years_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(availableYears, key = { it }) { yr ->
                                    FilterChip(
                                        selected = yr == selectedYear,
                                        onClick = { selectedYear = yr },
                                        label = { Text("$yr") },
                                        modifier = Modifier.testTag("consumption_year_chip_$yr")
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.16f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "إجمالي استهلاك سنة $selectedYear",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "${numberFormat.format(annualTotalKwh)} ك.و.س",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = alasalColors.gold,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("consumption_annual_total_kwh")
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "$annualTotalReadings قراءة مسجلة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "months_hint") {
                Text(
                    text = "الأشهر ($selectedYear) — اضغط على أي شهر لعرض النصف الأول والثاني والملخص النهائي:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()
                )
            }

            items(yearMonthlySummaries, key = { it.periodKey }) { monthSummary ->
                val hasReadings = monthSummary.totalReadingsCount > 0
                Card(
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .clickable {
                            selectedMonthNumber = monthSummary.month
                            pdfActionMessage = null
                        }
                        .testTag("consumption_month_card_${monthSummary.periodKey}"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasReadings) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        }
                    ),
                    border = BorderStroke(
                        width = if (hasReadings) 1.3.dp else 1.dp,
                        color = if (hasReadings) alasalColors.gold.copy(alpha = 0.7f) else alasalColors.cardBorder
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (hasReadings) 2.dp else 0.dp)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (hasReadings) alasalColors.navyBrand else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${monthSummary.month}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasReadings) alasalColors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = monthSummary.monthTitleAr,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "الفترة: ${monthSummary.periodKey} | ${monthSummary.totalReadingsCount} قراءة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (hasReadings) alasalColors.creditContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${numberFormat.format(monthSummary.totalMonthlyKwh)} ك.و.س",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasReadings) alasalColors.creditGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                            .testTag("month_preview_total_${monthSummary.periodKey}")
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "عرض تفاصيل ${monthSummary.monthTitleAr}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "النصف الأول: ${numberFormat.format(monthSummary.firstHalf.totalConsumedKwh)} ك.و.س (${monthSummary.firstHalfMetersCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "النصف الثاني: ${numberFormat.format(monthSummary.secondHalf.totalConsumedKwh)} ك.و.س (${monthSummary.secondHalfMetersCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyConsumptionDetailContent(
    summary: MonthlyConsumptionSummary,
    numberFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    pdfActionMessage: String?,
    onBackToMonths: () -> Unit,
    onExportPdf: () -> Unit,
    onPrintPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alasalColors = LocalAlasalColors.current
    var showFirstHalfList by remember(summary.periodKey) { mutableStateOf(true) }
    var showSecondHalfList by remember(summary.periodKey) { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("monthly_consumption_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "detail_header") {
            Column(
                modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackToMonths,
                        modifier = Modifier.testTag("month_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة لقائمة الأشهر"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${summary.monthTitleAr} — ${summary.year}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("month_detail_title")
                        )
                        Text(
                            text = "تفصيل الاستهلاك الصافي للنصف الأول والثاني والملخص الشهري النهائي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExportPdf,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = alasalColors.navyBrand,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_monthly_consumption_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("تصدير PDF", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onPrintPdf,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("print_monthly_consumption_pdf_button")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("طباعة التقرير", maxLines = 1)
                    }
                }

                if (pdfActionMessage != null) {
                    Surface(
                        color = alasalColors.creditContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, alasalColors.creditGreen.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = pdfActionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        )
                    }
                }
            }
        }

        // Section A: First Half (Days 1 - 15)
        item(key = "section_first_half") {
            HalfCycleSectionCard(
                badgeLabel = "أ — النصف الأول (1 - 15)",
                halfSummary = summary.firstHalf,
                accentColor = MaterialTheme.colorScheme.primary,
                expanded = showFirstHalfList,
                onToggleExpanded = { showFirstHalfList = !showFirstHalfList },
                numberFormat = numberFormat,
                dateFormat = dateFormat,
                cardTestTag = "first_half_section_card",
                totalTestTag = "first_half_total_kwh",
                toggleTestTag = "toggle_first_half_list_btn",
                modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()
            )
        }

        // Section B: Second Half (Days 16 - End of Month)
        item(key = "section_second_half") {
            HalfCycleSectionCard(
                badgeLabel = "ب — النصف الثاني (16 - نهاية الشهر)",
                halfSummary = summary.secondHalf,
                accentColor = alasalColors.gold,
                expanded = showSecondHalfList,
                onToggleExpanded = { showSecondHalfList = !showSecondHalfList },
                numberFormat = numberFormat,
                dateFormat = dateFormat,
                cardTestTag = "second_half_section_card",
                totalTestTag = "second_half_total_kwh",
                toggleTestTag = "toggle_second_half_list_btn",
                modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()
            )
        }

        // Section C: Final Monthly Summary Card at the bottom
        item(key = "section_final_summary") {
            Card(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
                    .testTag("monthly_final_summary_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = alasalColors.navyBrand),
                border = BorderStroke(1.8.dp, alasalColors.gold),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = alasalColors.electricYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "الملخص النهائي للشهر",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = alasalColors.gold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, alasalColors.gold)
                        ) {
                            Text(
                                text = summary.monthTitleAr,
                                style = MaterialTheme.typography.labelMedium,
                                color = alasalColors.gold,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.White.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "الإجمالي الكلي للكيلوواطات في ${summary.monthTitleAr} ${summary.year}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "${numberFormat.format(summary.totalMonthlyKwh)} ك.و.س",
                                style = MaterialTheme.typography.headlineMedium,
                                color = alasalColors.electricYellow,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("monthly_grand_total_kwh")
                            )
                            Text(
                                text = "النصف الأول (${numberFormat.format(summary.firstHalf.totalConsumedKwh)}) + النصف الثاني (${numberFormat.format(summary.secondHalf.totalConsumedKwh)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryCountTile(
                            label = "عدادات النصف الأول",
                            value = "${summary.firstHalfMetersCount}",
                            accent = Color.White,
                            testTag = "monthly_first_half_count",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCountTile(
                            label = "عدادات النصف الثاني",
                            value = "${summary.secondHalfMetersCount}",
                            accent = alasalColors.gold,
                            testTag = "monthly_second_half_count",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryCountTile(
                            label = "إجمالي القراءات",
                            value = "${summary.totalReadingsCount}",
                            accent = alasalColors.electricYellow,
                            testTag = "monthly_total_readings_count",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HalfCycleSectionCard(
    badgeLabel: String,
    halfSummary: HalfCycleSummary,
    accentColor: Color,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    numberFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    cardTestTag: String,
    totalTestTag: String,
    toggleTestTag: String,
    modifier: Modifier = Modifier
) {
    val alasalColors = LocalAlasalColors.current
    Card(
        modifier = modifier.testTag(cardTestTag),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = halfSummary.titleAr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "عدد العدادات المقروءة: ${halfSummary.metersReadCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "إجمالي الدورة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${numberFormat.format(halfSummary.totalConsumedKwh)} ك.و.س",
                        style = MaterialTheme.typography.titleLarge,
                        color = alasalColors.creditGreen,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag(totalTestTag)
                    )
                }
            }

            OutlinedButton(
                onClick = onToggleExpanded,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(toggleTestTag)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = if (expanded) {
                        "إخفاء قائمة المشتركين (${halfSummary.items.size})"
                    } else {
                        "عرض قائمة المشتركين واستهلاك كل مشترك (${halfSummary.items.size})"
                    }
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (halfSummary.items.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ElectricMeter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "لا توجد قراءات مسجلة في ${halfSummary.shortTitleAr} من هذا الشهر حتى الآن.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        halfSummary.items.forEach { item ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(0.8.dp, alasalColors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("half_${halfSummary.readingHalf}_item_${item.readingId}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${item.subscriberName} (${item.subscriberCode})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "السابقة: ${item.previousReading} ← الحالية: ${item.currentReading} | ${dateFormat.format(Date(item.readingDate))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = alasalColors.creditContainer
                                    ) {
                                        Text(
                                            text = "${numberFormat.format(item.consumedKwh)} ك.و.س",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = alasalColors.creditGreen,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCountTile(
    label: String,
    value: String,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag(testTag)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
    }
}
