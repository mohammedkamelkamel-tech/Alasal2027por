package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.domain.finance.ConsumptionSummaryCalculator
import com.example.domain.finance.StationReportGenerator
import com.example.service.InvoiceShareAndPrintService
import com.example.ui.components.AlasalBrandHeader
import com.example.ui.components.StatMetricCard
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.theme.LocalAlasalColors
import com.example.ui.viewmodel.FeedbackMessage
import com.example.ui.viewmodel.FoundationUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: FoundationUiState.Ready,
    isDarkTheme: Boolean,
    feedback: FeedbackMessage?,
    invoices: List<InvoiceEntity> = emptyList(),
    collectionsWithReceipts: List<CollectionWithReceipt> = emptyList(),
    subscribers: List<SubscriberEntity> = state.subscribers,
    readings: List<MeterReadingEntity> = emptyList(),
    onDismissFeedback: () -> Unit,
    onToggleTheme: () -> Unit,
    onVerifySchema: () -> Unit,
    onScheduleAuditWorker: () -> Unit,
    onRequestSafBackup: () -> Unit,
    onNavigateToTotalConsumption: () -> Unit = {},
    onNavigateToCollections: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToFieldCollection: () -> Unit = onNavigateToReports,
    onNavigateToReminders: () -> Unit = {},
    onNavigateToReadings: () -> Unit = {},
    onNavigateToSubscribers: () -> Unit = {},
    onNavigateToAccounts: () -> Unit = {},
    onNavigateToSyncBackup: () -> Unit = {},
    onLockApp: (() -> Unit)? = null,
    activeUserName: String? = null,
    onOpenInvoice: (Long) -> Unit = {},
    onOpenReceipt: (ReceiptEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val alasalColors = LocalAlasalColors.current
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("ar")) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.forLanguageTag("ar")) }
    val subMap = remember(subscribers) { subscribers.associateBy { it.id } }

    val activeTariffLabel = state.dashboard.primaryTariff?.let {
        "${it.pricePerKwh} / ك.و.س"
    } ?: "غير محددة"

    val currentMonthConsumption = remember(readings, subscribers, invoices) {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        ConsumptionSummaryCalculator.buildMonthSummary(
            year = year,
            month = month,
            readings = readings,
            subscribers = subscribers,
            invoicedReadingIds = invoices.mapNotNull { it.meterReadingId }.toSet()
        )
    }

    val latestInvoices = remember(invoices) {
        invoices.sortedByDescending { it.issuedAt }.take(4)
    }
    val latestCollections = remember(collectionsWithReceipts) {
        collectionsWithReceipts.sortedByDescending { it.collection.collectedAt }.take(4)
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
                .testTag("dashboard_screen"),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.widthIn(max = 800.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AlasalBrandHeader(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        themeIcon = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        onLockApp = onLockApp
                    )

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = alasalColors.gold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, alasalColors.gold.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.phase_four_badge),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (!activeUserName.isNullOrBlank()) "$activeUserName | 14/14" else "14/14 جدول نشط",
                                style = MaterialTheme.typography.labelMedium,
                                color = alasalColors.creditGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable(onClick = onNavigateToAccounts)
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            item(key = "shortcuts") {
                Column(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "اختصارات العمليات الأساسية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        modifier = Modifier.testTag("dashboard_shortcuts_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.Bolt,
                                label = stringResource(R.string.title_total_consumption),
                                accent = alasalColors.gold,
                                testTag = "shortcut_total_consumption",
                                onClick = onNavigateToTotalConsumption
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.Payments,
                                label = "التحصيل والإيصالات",
                                accent = alasalColors.creditGreen,
                                testTag = "shortcut_collections",
                                onClick = onNavigateToCollections
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.Sync,
                                label = "المزامنة والنسخ الآمن",
                                accent = alasalColors.gold,
                                testTag = "shortcut_sync_backup",
                                onClick = onNavigateToSyncBackup
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.AdminPanelSettings,
                                label = "الحسابات والصلاحيات",
                                accent = MaterialTheme.colorScheme.primary,
                                testTag = "shortcut_accounts",
                                onClick = onNavigateToAccounts
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.LocationOn,
                                label = "كشف النزول الميداني",
                                accent = alasalColors.arrearsRed,
                                testTag = "shortcut_field_collection",
                                onClick = onNavigateToFieldCollection
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.Assessment,
                                label = "التقارير وكشف الحساب",
                                accent = MaterialTheme.colorScheme.primary,
                                testTag = "shortcut_reports",
                                onClick = onNavigateToReports
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.NotificationsActive,
                                label = "التذكيرات والإشعارات",
                                accent = alasalColors.gold,
                                testTag = "shortcut_reminders",
                                onClick = onNavigateToReminders
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.ElectricMeter,
                                label = "تسجيل القراءات",
                                accent = MaterialTheme.colorScheme.secondary,
                                testTag = "shortcut_readings",
                                onClick = onNavigateToReadings
                            )
                        }
                        item {
                            DashboardShortcutButton(
                                icon = Icons.Filled.People,
                                label = "المشتركون والأسعار",
                                accent = MaterialTheme.colorScheme.primary,
                                testTag = "shortcut_subscribers",
                                onClick = onNavigateToSubscribers
                            )
                        }
                    }
                }
            }

            item(key = "total_consumption_featured_card") {
                Card(
                    onClick = onNavigateToTotalConsumption,
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .testTag("dashboard_total_consumption_card"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.5.dp, alasalColors.gold)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    shape = MaterialTheme.shapes.small,
                                    color = alasalColors.gold.copy(alpha = 0.22f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = stringResource(R.string.title_total_consumption),
                                        tint = alasalColors.gold,
                                        modifier = Modifier.padding(8.dp).size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.title_total_consumption),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = "${currentMonthConsumption.monthTitleAr} ${currentMonthConsumption.year} — شهري ونصف شهري",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${numberFormat.format(currentMonthConsumption.totalMonthlyKwh)} ك.و.س",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = alasalColors.gold,
                                    modifier = Modifier.testTag("dashboard_current_month_kwh")
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "فتح إجمالي الاستهلاك",
                                    tint = alasalColors.gold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = "القراءة الأولى (1-15)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "${numberFormat.format(currentMonthConsumption.firstHalf.totalConsumedKwh)} ك.و.س (${currentMonthConsumption.firstHalf.metersReadCount} عداد)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = "القراءة الثانية (16-آخر الشهر)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "${numberFormat.format(currentMonthConsumption.secondHalf.totalConsumedKwh)} ك.و.س (${currentMonthConsumption.secondHalf.metersReadCount} عداد)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "kpis_row_1") {
                Column(
                    modifier = Modifier.widthIn(max = 800.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_welcome),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("dashboard_welcome_title")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatMetricCard(
                            title = stringResource(R.string.stat_subscribers),
                            value = numberFormat.format(state.dashboard.subscriberCount),
                            subtitle = "جدول المشتركين النشط",
                            icon = Icons.Filled.People,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            testTag = "stat_card_subscribers"
                        )
                        StatMetricCard(
                            title = stringResource(R.string.stat_invoices),
                            value = numberFormat.format(state.dashboard.invoiceCount),
                            subtitle = "مدفوعة ${state.dashboard.paidInvoicesCount} | جزئية ${state.dashboard.partialInvoicesCount} | غير مدفوعة ${state.dashboard.unpaidInvoicesCount}",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            testTag = "stat_card_invoices"
                        )
                    }
                }
            }

            item(key = "kpis_row_2") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMetricCard(
                        title = "إجمالي المستحقات",
                        value = numberFormat.format(state.dashboard.totalReceivables),
                        subtitle = "صافي الفواتير المستحقة",
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accentColor = alasalColors.gold,
                        modifier = Modifier.weight(1f),
                        testTag = "stat_card_receivables"
                    )
                    StatMetricCard(
                        title = "إجمالي المحصّل",
                        value = numberFormat.format(state.dashboard.totalCollected),
                        subtitle = "${numberFormat.format(state.dashboard.collectionCount)} عملية تحصيل",
                        icon = Icons.Filled.Payments,
                        accentColor = alasalColors.creditGreen,
                        containerColor = alasalColors.creditContainer.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                        testTag = "stat_card_collections"
                    )
                }
            }

            item(key = "kpis_row_3") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatMetricCard(
                        title = stringResource(R.string.stat_arrears),
                        value = numberFormat.format(state.dashboard.totalArrears),
                        subtitle = "المتأخرات النشطة",
                        icon = Icons.Filled.WarningAmber,
                        accentColor = alasalColors.arrearsRed,
                        containerColor = alasalColors.arrearsContainer.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                        testTag = "stat_card_arrears"
                    )
                    StatMetricCard(
                        title = stringResource(R.string.stat_credits),
                        value = numberFormat.format(state.dashboard.totalCredits),
                        subtitle = "الأرصدة الدائنة المتاحة",
                        icon = Icons.Filled.AccountBalanceWallet,
                        accentColor = alasalColors.creditGreen,
                        containerColor = alasalColors.creditContainer.copy(alpha = 0.45f),
                        modifier = Modifier.weight(1f),
                        testTag = "stat_card_credits"
                    )
                }
            }

            item(key = "kpis_row_tariff") {
                StatMetricCard(
                    title = stringResource(R.string.stat_active_tariff),
                    value = activeTariffLabel,
                    subtitle = state.dashboard.primaryTariff?.title ?: "التعرفة الأساسية المعتمدة",
                    icon = Icons.Filled.Bolt,
                    accentColor = alasalColors.gold,
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    testTag = "stat_card_tariff"
                )
            }

            item(key = "charts") {
                Card(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, alasalColors.cardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "الرسوم البيانية الحية للإيرادات والتحصيل وحالات الفواتير",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        val metrics = listOf(
                            Triple("المستحقات", state.dashboard.totalReceivables, alasalColors.gold),
                            Triple("المحصّل", state.dashboard.totalCollected, alasalColors.creditGreen),
                            Triple("المتأخرات", state.dashboard.totalArrears, alasalColors.arrearsRed),
                            Triple("الدائن", state.dashboard.totalCredits, MaterialTheme.colorScheme.primary)
                        )
                        val maxMetric = (metrics.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .testTag("dashboard_revenue_chart")
                        ) {
                            val barCount = metrics.size
                            val slotWidth = size.width / barCount
                            val barWidth = (slotWidth * 0.48f).coerceAtMost(52.dp.toPx())
                            metrics.forEachIndexed { idx, (_, value, color) ->
                                val ratio = (value / maxMetric).toFloat().coerceIn(0.04f, 1f)
                                val barHeight = (size.height - 16f) * ratio
                                val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                                val y = size.height - barHeight
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            metrics.forEach { (label, value, color) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                                    Text(numberFormat.format(value), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        val totalInv = (state.dashboard.paidInvoicesCount +
                            state.dashboard.partialInvoicesCount +
                            state.dashboard.unpaidInvoicesCount).coerceAtLeast(1)
                        val paidFrac = state.dashboard.paidInvoicesCount.toFloat() / totalInv
                        val partialFrac = state.dashboard.partialInvoicesCount.toFloat() / totalInv
                        val unpaidFrac = state.dashboard.unpaidInvoicesCount.toFloat() / totalInv

                        Text(
                            text = "توزيع حالات الفواتير (مدفوعة / جزئية / غير مدفوعة)",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .testTag("dashboard_invoice_status_chart")
                        ) {
                            val w = size.width
                            var startX = 0f
                            val segments = listOf(
                                paidFrac to alasalColors.creditGreen,
                                partialFrac to alasalColors.gold,
                                unpaidFrac to alasalColors.arrearsRed
                            )
                            segments.forEach { (frac, color) ->
                                if (frac > 0f) {
                                    val segW = w * frac
                                    drawRect(
                                        color = color,
                                        topLeft = Offset(startX, 0f),
                                        size = Size(segW, size.height)
                                    )
                                    startX += segW
                                }
                            }
                            if (startX == 0f) {
                                drawRect(
                                    color = Color(0xFFCBD5E1),
                                    topLeft = Offset.Zero,
                                    size = size
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("مدفوعة: ${state.dashboard.paidInvoicesCount}", style = MaterialTheme.typography.labelSmall, color = alasalColors.creditGreen)
                            Text("جزئية: ${state.dashboard.partialInvoicesCount}", style = MaterialTheme.typography.labelSmall, color = alasalColors.gold)
                            Text("غير مدفوعة: ${state.dashboard.unpaidInvoicesCount}", style = MaterialTheme.typography.labelSmall, color = alasalColors.arrearsRed)
                        }
                    }
                }
            }

            item(key = "latest_invoices_header") {
                Row(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "أحدث الفواتير (${latestInvoices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "التحصيل السريع ←",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToCollections() }
                    )
                }
            }

            if (latestInvoices.isEmpty()) {
                item(key = "no_latest_invoices") {
                    Text(
                        text = "لا توجد فواتير صادرة بعد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()
                    )
                }
            } else {
                items(latestInvoices, key = { "dash_inv_${it.id}" }) { inv ->
                    val subName = subMap[inv.subscriberId]?.fullName ?: "مشترك #${inv.subscriberId}"
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .clickable { onOpenInvoice(inv.id) }
                            .testTag("dashboard_latest_invoice_${inv.invoiceNumber}"),
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${inv.invoiceNumber} — $subName", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "الصافي: ${inv.netPayableAmount} | المتبقي: ${inv.remainingAmount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = InvoiceShareAndPrintService.translateStatus(inv.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (inv.status == "PAID") alasalColors.creditGreen else alasalColors.arrearsRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item(key = "latest_collections_header") {
                Row(
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "أحدث التحصيلات والإيصالات (${latestCollections.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "عرض الكل ←",
                        style = MaterialTheme.typography.labelMedium,
                        color = alasalColors.creditGreen,
                        modifier = Modifier.clickable { onNavigateToCollections() }
                    )
                }
            }

            if (latestCollections.isEmpty()) {
                item(key = "no_latest_collections") {
                    Text(
                        text = "لا توجد تحصيلات مسجلة بعد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth()
                    )
                }
            } else {
                items(latestCollections, key = { "dash_col_${it.collection.id}" }) { item ->
                    val col = item.collection
                    val rcp = item.receipt
                    val subName = subMap[col.subscriberId]?.fullName ?: rcp?.subscriberNameSnapshot ?: "-"
                    Card(
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                            .clickable(enabled = rcp != null) { rcp?.let(onOpenReceipt) }
                            .testTag("dashboard_latest_collection_${rcp?.receiptNumber ?: col.collectionReference}"),
                        shape = MaterialTheme.shapes.small,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                        border = BorderStroke(1.dp, alasalColors.cardBorder.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${rcp?.receiptNumber ?: col.collectionReference} — $subName",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "المحصل: ${col.collectorNameSnapshot} | ${StationReportGenerator.translatePaymentMethod(col.paymentMethod)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "+${col.amountPaid}",
                                style = MaterialTheme.typography.titleSmall,
                                color = alasalColors.creditGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item(key = "actions") {
                Column(
                    modifier = Modifier.widthIn(max = 800.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.section_quick_actions),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onVerifySchema,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("verify_schema_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FactCheck,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.action_verify_schema),
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onScheduleAuditWorker,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("run_worker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.action_run_audit_worker),
                                maxLines = 1
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRequestSafBackup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_saf_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.action_backup_saf))
                    }
                }
            }

            item(key = "audit_header") {
                Row(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = alasalColors.gold
                    )
                    Text(
                        text = "أحدث سجلات العمليات والتدقيق المالي",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            items(state.auditLogs.take(5), key = { it.id }) { log ->
                Card(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ),
                    border = BorderStroke(1.dp, alasalColors.cardBorder.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = log.operationType,
                                style = MaterialTheme.typography.labelMedium,
                                color = alasalColors.gold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormat.format(Date(log.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = log.detailsAr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardShortcutButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
