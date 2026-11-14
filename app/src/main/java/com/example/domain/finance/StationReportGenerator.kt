package com.example.domain.finance

import com.example.data.local.entity.ArrearsEntity
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.UserEntity
import com.example.service.InvoiceShareAndPrintService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportCategory(val titleAr: String) {
    ALL_INVOICES("تقرير الفواتير الشامل"),
    PAID_INVOICES("تقرير الفواتير المدفوعة"),
    UNPAID_INVOICES("تقرير الفواتير غير المدفوعة"),
    PARTIAL_INVOICES("تقرير الفواتير المدفوعة جزئياً"),
    COLLECTIONS("تقرير التحصيلات والمدفوعات"),
    ARREARS("تقرير المتأخرات"),
    CREDITS("تقرير الأرصدة الدائنة"),
    CONSUMPTION_READINGS("تقرير الاستهلاك والقراءات"),
    SUBSCRIBER_STATEMENT("كشف حساب المشترك")
}

enum class DateRangePreset(val titleAr: String) {
    ALL("كل الفترات"),
    TODAY("يومي (اليوم)"),
    THIS_MONTH("شهري (هذا الشهر)"),
    CUSTOM_RANGE("بين تاريخين")
}

data class ReportFilter(
    val category: ReportCategory = ReportCategory.ALL_INVOICES,
    val subscriberId: Long? = null,
    val collectorUserId: Long? = null,
    val datePreset: DateRangePreset = DateRangePreset.ALL,
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null
)

data class ReportRow(
    val idKey: String,
    val primaryTitle: String,
    val secondaryDetail: String,
    val dateMillis: Long,
    val amountLabel: String,
    val statusLabel: String,
    val isCreditAccent: Boolean = false,
    val isArrearsAccent: Boolean = false
)

data class GeneratedReport(
    val title: String,
    val filterSummary: String,
    val columns: List<String>,
    val tableRows: List<List<String>>,
    val uiRows: List<ReportRow>,
    val summaryTotals: List<Pair<String, String>>
)

data class SubscriberAccountStatement(
    val subscriber: SubscriberEntity,
    val invoices: List<InvoiceEntity>,
    val collectionsWithReceipts: List<CollectionWithReceipt>,
    val readings: List<MeterReadingEntity>,
    val creditTransactions: List<CreditBalanceEntity>,
    val totalConsumedKwh: Double,
    val totalBilledEnergy: Double,
    val totalCollected: Double,
    val activeArrears: Double,
    val creditBalance: Double,
    val netFinalBalance: Double
)

enum class FieldDebtFilter(val titleAr: String) {
    ARREARS_ONLY("عليه متأخرات فقط"),
    ALL("جميع المشتركين"),
    NO_ARREARS("بدون متأخرات")
}

data class FieldCollectionSheetItem(
    val subscriberId: Long,
    val subscriberCode: String,
    val fullName: String,
    val address: String,
    val phoneNumber: String,
    val previousMeterReading: Double,
    val currentArrears: Double,
    val creditBalance: Double,
    val customKwhPrice: Double,
    val isActive: Boolean
)

/**
 * Pure domain reporting and account-statement calculator working directly over live Room snapshots.
 */
object StationReportGenerator {

    fun resolveTimeBounds(
        preset: DateRangePreset,
        nowMillis: Long = System.currentTimeMillis(),
        customStartMillis: Long? = null,
        customEndMillis: Long? = null
    ): Pair<Long, Long>? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (preset) {
            DateRangePreset.ALL -> null
            DateRangePreset.TODAY -> {
                val start = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                start to end
            }
            DateRangePreset.THIS_MONTH -> {
                val start = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                start to end
            }
            DateRangePreset.CUSTOM_RANGE -> {
                val s = customStartMillis ?: 0L
                val e = customEndMillis ?: Long.MAX_VALUE
                minOf(s, e) to maxOf(s, e)
            }
        }
    }

    private fun inBounds(timestamp: Long, bounds: Pair<Long, Long>?): Boolean {
        if (bounds == null) return true
        return timestamp in bounds.first..bounds.second
    }

    fun generateReport(
        filter: ReportFilter,
        subscribers: List<SubscriberEntity>,
        invoices: List<InvoiceEntity>,
        collectionsWithReceipts: List<CollectionWithReceipt>,
        readings: List<MeterReadingEntity>,
        arrears: List<ArrearsEntity>,
        creditTransactions: List<CreditBalanceEntity>,
        users: List<UserEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): GeneratedReport {
        val arabicLocale = Locale.forLanguageTag("ar")
        val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", arabicLocale)
        val subMap = subscribers.associateBy { it.id }
        val userMap = users.associateBy { it.id }
        val bounds = resolveTimeBounds(
            preset = filter.datePreset,
            nowMillis = nowMillis,
            customStartMillis = filter.customStartMillis,
            customEndMillis = filter.customEndMillis
        )

        val subLabel = filter.subscriberId?.let { subMap[it]?.fullName } ?: "جميع المشتركين"
        val collectorLabel = filter.collectorUserId?.let { userMap[it]?.fullName } ?: "جميع المحصلين"
        val filterSummary = "المشترك: $subLabel | المحصل: $collectorLabel | الفترة: ${filter.datePreset.titleAr}"

        val collectorInvoiceIds = filter.collectorUserId?.let { colId ->
            collectionsWithReceipts
                .filter { it.collection.collectedByUserId == colId && it.collection.invoiceId != null }
                .mapNotNull { it.collection.invoiceId }
                .toSet()
        }

        return when (filter.category) {
            ReportCategory.ALL_INVOICES,
            ReportCategory.PAID_INVOICES,
            ReportCategory.UNPAID_INVOICES,
            ReportCategory.PARTIAL_INVOICES -> {
                val filteredInvoices = invoices.filter { inv ->
                    (filter.subscriberId == null || inv.subscriberId == filter.subscriberId) &&
                        (collectorInvoiceIds == null || inv.id in collectorInvoiceIds) &&
                        inBounds(inv.issuedAt, bounds) &&
                        when (filter.category) {
                            ReportCategory.PAID_INVOICES -> inv.status == "PAID"
                            ReportCategory.UNPAID_INVOICES -> inv.status == "UNPAID" && inv.carriedOverToInvoiceId == null
                            ReportCategory.PARTIAL_INVOICES -> inv.status == "PARTIAL" && inv.carriedOverToInvoiceId == null
                            else -> true
                        }
                }
                val totalNet = FinancialCalculator.roundCurrency(filteredInvoices.sumOf { it.netPayableAmount })
                val totalPaid = FinancialCalculator.roundCurrency(filteredInvoices.sumOf { it.paidAmount })
                val totalRemaining = FinancialCalculator.roundCurrency(
                    filteredInvoices.filter { it.carriedOverToInvoiceId == null }.sumOf { it.remainingAmount }
                )

                GeneratedReport(
                    title = filter.category.titleAr,
                    filterSummary = filterSummary,
                    columns = listOf("الفاتورة", "المشترك", "الاستهلاك", "الصافي", "المدفوع", "المتبقي", "الحالة"),
                    tableRows = filteredInvoices.map { inv ->
                        val subName = subMap[inv.subscriberId]?.fullName ?: "-"
                        listOf(
                            inv.invoiceNumber,
                            subName,
                            "${inv.consumedKwh} ك.و.س",
                            "${inv.netPayableAmount}",
                            "${inv.paidAmount}",
                            "${inv.remainingAmount}",
                            InvoiceShareAndPrintService.translateStatus(inv.status)
                        )
                    },
                    uiRows = filteredInvoices.map { inv ->
                        val subName = subMap[inv.subscriberId]?.fullName ?: "-"
                        ReportRow(
                            idKey = "inv_${inv.id}",
                            primaryTitle = "${inv.invoiceNumber} — $subName",
                            secondaryDetail = "استهلاك: ${inv.consumedKwh} × ${inv.unitPriceApplied} | مدفوع: ${inv.paidAmount} | متبقي: ${inv.remainingAmount}",
                            dateMillis = inv.issuedAt,
                            amountLabel = "صافي: ${inv.netPayableAmount}",
                            statusLabel = InvoiceShareAndPrintService.translateStatus(inv.status),
                            isCreditAccent = inv.status == "PAID",
                            isArrearsAccent = inv.remainingAmount > 0.0 && inv.status != "CARRIED_OVER"
                        )
                    },
                    summaryTotals = listOf(
                        "عدد الفواتير" to "${filteredInvoices.size}",
                        "إجمالي الصافي" to "$totalNet",
                        "إجمالي المدفوع" to "$totalPaid",
                        "المتبقي النشط" to "$totalRemaining"
                    )
                )
            }

            ReportCategory.COLLECTIONS -> {
                val filtered = collectionsWithReceipts.filter { item ->
                    val col = item.collection
                    (filter.subscriberId == null || col.subscriberId == filter.subscriberId) &&
                        (filter.collectorUserId == null || col.collectedByUserId == filter.collectorUserId) &&
                        inBounds(col.collectedAt, bounds)
                }
                val totalCollected = FinancialCalculator.roundCurrency(filtered.sumOf { it.collection.amountPaid })
                val totalSurplusCredit = FinancialCalculator.roundCurrency(filtered.sumOf { it.collection.addedToCredit })
                GeneratedReport(
                    title = filter.category.titleAr,
                    filterSummary = filterSummary,
                    columns = listOf("الإيصال", "المشترك", "المحصل", "المبلغ", "الطريقة", "التاريخ"),
                    tableRows = filtered.map { item ->
                        val col = item.collection
                        val rcpNum = item.receipt?.receiptNumber ?: col.collectionReference
                        val subName = subMap[col.subscriberId]?.fullName ?: item.receipt?.subscriberNameSnapshot ?: "-"
                        listOf(
                            rcpNum,
                            subName,
                            col.collectorNameSnapshot,
                            "${col.amountPaid}",
                            translatePaymentMethod(col.paymentMethod),
                            dateFormat.format(Date(col.collectedAt))
                        )
                    },
                    uiRows = filtered.map { item ->
                        val col = item.collection
                        val rcpNum = item.receipt?.receiptNumber ?: col.collectionReference
                        val subName = subMap[col.subscriberId]?.fullName ?: item.receipt?.subscriberNameSnapshot ?: "-"
                        ReportRow(
                            idKey = "col_${col.id}",
                            primaryTitle = "$rcpNum — $subName",
                            secondaryDetail = "المحصل: ${col.collectorNameSnapshot} | ${translatePaymentMethod(col.paymentMethod)} | متبقي بعدها: ${col.remainingAfterSnapshot}",
                            dateMillis = col.collectedAt,
                            amountLabel = "+${col.amountPaid}",
                            statusLabel = if (col.addedToCredit > 0.0) "دائن +${col.addedToCredit}" else "مسدّد",
                            isCreditAccent = true
                        )
                    },
                    summaryTotals = listOf(
                        "عدد التحصيلات" to "${filtered.size}",
                        "إجمالي المحصّل" to "$totalCollected",
                        "الفائض المضاف للدائن" to "$totalSurplusCredit"
                    )
                )
            }

            ReportCategory.ARREARS -> {
                val activeArrearsSubscribers = subscribers.filter { sub ->
                    (filter.subscriberId == null || sub.id == filter.subscriberId) &&
                        sub.currentArrearsAmount > 0.0
                }
                val filteredInvoicesWithArrears = invoices.filter { inv ->
                    (filter.subscriberId == null || inv.subscriberId == filter.subscriberId) &&
                        inv.carriedOverToInvoiceId == null &&
                        inv.remainingAmount > 0.0 &&
                        inv.status in listOf("UNPAID", "PARTIAL") &&
                        inBounds(inv.issuedAt, bounds)
                }
                val totalArrears = FinancialCalculator.roundCurrency(filteredInvoicesWithArrears.sumOf { it.remainingAmount })
                GeneratedReport(
                    title = filter.category.titleAr,
                    filterSummary = filterSummary,
                    columns = listOf("الفاتورة", "المشترك", "المطلوب", "المدفوع", "المتأخرات المتبقية"),
                    tableRows = filteredInvoicesWithArrears.map { inv ->
                        val subName = subMap[inv.subscriberId]?.fullName ?: "-"
                        listOf(
                            inv.invoiceNumber,
                            subName,
                            "${inv.netPayableAmount}",
                            "${inv.paidAmount}",
                            "${inv.remainingAmount}"
                        )
                    },
                    uiRows = filteredInvoicesWithArrears.map { inv ->
                        val subName = subMap[inv.subscriberId]?.fullName ?: "-"
                        ReportRow(
                            idKey = "arr_${inv.id}",
                            primaryTitle = "$subName — ${inv.invoiceNumber}",
                            secondaryDetail = "صافي: ${inv.netPayableAmount} | مدفوع: ${inv.paidAmount} | سجلات أرشيف الترحيل: ${arrears.count { it.subscriberId == inv.subscriberId }}",
                            dateMillis = inv.issuedAt,
                            amountLabel = "متأخرات: ${inv.remainingAmount}",
                            statusLabel = InvoiceShareAndPrintService.translateStatus(inv.status),
                            isArrearsAccent = true
                        )
                    },
                    summaryTotals = listOf(
                        "مشتركون عليهم متأخرات" to "${activeArrearsSubscribers.size}",
                        "إجمالي المتأخرات النشطة" to "$totalArrears"
                    )
                )
            }

            ReportCategory.CREDITS -> {
                val filteredCredits = creditTransactions.filter { tx ->
                    (filter.subscriberId == null || tx.subscriberId == filter.subscriberId) &&
                        inBounds(tx.recordedAt, bounds)
                }
                val totalActiveCredits = FinancialCalculator.roundCurrency(
                    subscribers.filter { filter.subscriberId == null || it.id == filter.subscriberId }
                        .sumOf { it.currentCreditBalance }
                )
                GeneratedReport(
                    title = filter.category.titleAr,
                    filterSummary = filterSummary,
                    columns = listOf("المشترك", "نوع الحركة", "التغير", "الرصيد بعد", "ملاحظات"),
                    tableRows = filteredCredits.map { tx ->
                        val subName = subMap[tx.subscriberId]?.fullName ?: "-"
                        listOf(
                            subName,
                            tx.transactionType,
                            "${tx.amountDelta}",
                            "${tx.balanceAfter}",
                            tx.notes
                        )
                    },
                    uiRows = filteredCredits.map { tx ->
                        val subName = subMap[tx.subscriberId]?.fullName ?: "-"
                        ReportRow(
                            idKey = "crd_${tx.id}",
                            primaryTitle = "$subName (${tx.transactionType})",
                            secondaryDetail = "${tx.notes} — الرصيد بعد الحركة: ${tx.balanceAfter}",
                            dateMillis = tx.recordedAt,
                            amountLabel = "${tx.amountDelta}",
                            statusLabel = "رصيد: ${tx.balanceAfter}",
                            isCreditAccent = tx.amountDelta >= 0.0
                        )
                    },
                    summaryTotals = listOf(
                        "حركات الدائن" to "${filteredCredits.size}",
                        "الأرصدة الدائنة المتاحة حالياً" to "$totalActiveCredits"
                    )
                )
            }

            ReportCategory.CONSUMPTION_READINGS -> {
                val filteredReadings = readings.filter { r ->
                    (filter.subscriberId == null || r.subscriberId == filter.subscriberId) &&
                        inBounds(r.readingDate, bounds)
                }
                val totalKwh = FinancialCalculator.roundCurrency(filteredReadings.sumOf { it.consumedKwh })
                GeneratedReport(
                    title = filter.category.titleAr,
                    filterSummary = filterSummary,
                    columns = listOf("المشترك", "الفترة", "السابقة", "الحالية", "الاستهلاك (ك.و.س)"),
                    tableRows = filteredReadings.map { r ->
                        val subName = subMap[r.subscriberId]?.fullName ?: "-"
                        listOf(
                            subName,
                            r.billingPeriod,
                            "${r.previousReading}",
                            "${r.currentReading}",
                            "${r.consumedKwh}"
                        )
                    },
                    uiRows = filteredReadings.map { r ->
                        val subName = subMap[r.subscriberId]?.fullName ?: "-"
                        ReportRow(
                            idKey = "rd_${r.id}",
                            primaryTitle = "$subName — فترة ${r.billingPeriod}",
                            secondaryDetail = "السابقة: ${r.previousReading} ← الحالية: ${r.currentReading}",
                            dateMillis = r.readingDate,
                            amountLabel = "${r.consumedKwh} ك.و.س",
                            statusLabel = if (r.isLowReadingOverride) "تجاوز استثنائي" else "قراءة معتمدة",
                            isArrearsAccent = r.isLowReadingOverride
                        )
                    },
                    summaryTotals = listOf(
                        "عدد القراءات" to "${filteredReadings.size}",
                        "إجمالي الاستهلاك" to "$totalKwh ك.و.س"
                    )
                )
            }

            ReportCategory.SUBSCRIBER_STATEMENT -> {
                val targetSubscriber = filter.subscriberId?.let { subMap[it] } ?: subscribers.firstOrNull()
                if (targetSubscriber == null) {
                    GeneratedReport(
                        title = "كشف حساب المشترك",
                        filterSummary = filterSummary,
                        columns = listOf("البيان", "القيمة"),
                        tableRows = emptyList(),
                        uiRows = emptyList(),
                        summaryTotals = listOf("الرصيد النهائي" to "0.0")
                    )
                } else {
                    val statement = buildSubscriberStatement(
                        subscriber = targetSubscriber,
                        invoices = invoices.filter { inBounds(it.issuedAt, bounds) },
                        collectionsWithReceipts = collectionsWithReceipts.filter {
                            (filter.collectorUserId == null || it.collection.collectedByUserId == filter.collectorUserId) &&
                                inBounds(it.collection.collectedAt, bounds)
                        },
                        readings = readings.filter { inBounds(it.readingDate, bounds) },
                        creditTransactions = creditTransactions.filter { inBounds(it.recordedAt, bounds) }
                    )
                    val combinedRows = buildList {
                        statement.invoices.forEach { inv ->
                            add(
                                ReportRow(
                                    idKey = "st_inv_${inv.id}",
                                    primaryTitle = "فاتورة ${inv.invoiceNumber} (${inv.billingPeriod})",
                                    secondaryDetail = "استهلاك: ${inv.consumedKwh} ك.و.س | قيمة الطاقة: ${inv.energyAmount} | مرحّل: ${inv.previousArrearsApplied} | متبقي: ${inv.remainingAmount}",
                                    dateMillis = inv.issuedAt,
                                    amountLabel = "صافي: ${inv.netPayableAmount}",
                                    statusLabel = InvoiceShareAndPrintService.translateStatus(inv.status),
                                    isArrearsAccent = inv.remainingAmount > 0.0 && inv.status != "CARRIED_OVER"
                                )
                            )
                        }
                        statement.collectionsWithReceipts.forEach { item ->
                            val rcp = item.receipt?.receiptNumber ?: item.collection.collectionReference
                            add(
                                ReportRow(
                                    idKey = "st_col_${item.collection.id}",
                                    primaryTitle = "تحصيل وإيصال $rcp",
                                    secondaryDetail = "المحصل: ${item.collection.collectorNameSnapshot} | الطريقة: ${translatePaymentMethod(item.collection.paymentMethod)}",
                                    dateMillis = item.collection.collectedAt,
                                    amountLabel = "مدفوع: ${item.collection.amountPaid}",
                                    statusLabel = "متبقي بعدها: ${item.collection.remainingAfterSnapshot}",
                                    isCreditAccent = true
                                )
                            )
                        }
                    }.sortedByDescending { it.dateMillis }

                    GeneratedReport(
                        title = "كشف حساب: ${targetSubscriber.fullName}",
                        filterSummary = "رقم: ${targetSubscriber.subscriberCode} | عداد: ${targetSubscriber.meterNumber} | ${filter.datePreset.titleAr}",
                        columns = listOf("التاريخ", "الحركة / المرجع", "التفاصيل", "المبلغ", "الحالة / المتبقي"),
                        tableRows = combinedRows.map { row ->
                            listOf(
                                dateFormat.format(Date(row.dateMillis)),
                                row.primaryTitle,
                                row.secondaryDetail,
                                row.amountLabel,
                                row.statusLabel
                            )
                        },
                        uiRows = combinedRows,
                        summaryTotals = listOf(
                            "إجمالي الاستهلاك" to "${statement.totalConsumedKwh} ك.و.س",
                            "إجمالي فواتير الطاقة" to "${statement.totalBilledEnergy}",
                            "إجمالي التحصيلات" to "${statement.totalCollected}",
                            "المتأخرات النشطة" to "${statement.activeArrears}",
                            "الرصيد الدائن" to "${statement.creditBalance}",
                            "الرصيد النهائي (المستحق - الدائن)" to "${statement.netFinalBalance}"
                        )
                    )
                }
            }
        }
    }

    fun buildSubscriberStatement(
        subscriber: SubscriberEntity,
        invoices: List<InvoiceEntity>,
        collectionsWithReceipts: List<CollectionWithReceipt>,
        readings: List<MeterReadingEntity>,
        creditTransactions: List<CreditBalanceEntity>
    ): SubscriberAccountStatement {
        val subInvoices = invoices.filter { it.subscriberId == subscriber.id }.sortedByDescending { it.issuedAt }
        val subCollections = collectionsWithReceipts.filter { it.collection.subscriberId == subscriber.id }
            .sortedByDescending { it.collection.collectedAt }
        val subReadings = readings.filter { it.subscriberId == subscriber.id }.sortedByDescending { it.readingDate }
        val subCredits = creditTransactions.filter { it.subscriberId == subscriber.id }.sortedByDescending { it.recordedAt }

        val totalConsumed = FinancialCalculator.roundCurrency(subReadings.sumOf { it.consumedKwh })
        val totalEnergy = FinancialCalculator.roundCurrency(subInvoices.sumOf { it.energyAmount })
        val totalCollected = FinancialCalculator.roundCurrency(subCollections.sumOf { it.collection.amountPaid })
        val activeArrears = FinancialCalculator.roundCurrency(
            subInvoices.filter { it.carriedOverToInvoiceId == null && it.status in listOf("UNPAID", "PARTIAL") }
                .sumOf { it.remainingAmount }
        )
        val creditBalance = FinancialCalculator.roundCurrency(subscriber.currentCreditBalance)
        val netFinal = FinancialCalculator.roundCurrency(activeArrears - creditBalance)

        return SubscriberAccountStatement(
            subscriber = subscriber,
            invoices = subInvoices,
            collectionsWithReceipts = subCollections,
            readings = subReadings,
            creditTransactions = subCredits,
            totalConsumedKwh = totalConsumed,
            totalBilledEnergy = totalEnergy,
            totalCollected = totalCollected,
            activeArrears = activeArrears,
            creditBalance = creditBalance,
            netFinalBalance = netFinal
        )
    }

    fun buildFieldCollectionSheet(
        subscribers: List<SubscriberEntity>,
        readings: List<MeterReadingEntity>,
        areaFilter: String = "",
        debtFilter: FieldDebtFilter = FieldDebtFilter.ARREARS_ONLY,
        onlyActiveSubscribers: Boolean = true
    ): List<FieldCollectionSheetItem> {
        val latestReadingBySub = readings
            .groupBy { it.subscriberId }
            .mapValues { (_, list) ->
                list.maxWithOrNull(compareBy<MeterReadingEntity> { it.readingDate }.thenBy { it.id })
                    ?.currentReading ?: 0.0
            }
        val normalizedArea = areaFilter.trim().lowercase()
        return subscribers
            .filter { sub ->
                val activeMatch = !onlyActiveSubscribers || sub.isActive
                val areaMatch = normalizedArea.isEmpty() ||
                    sub.address.lowercase().contains(normalizedArea) ||
                    sub.fullName.lowercase().contains(normalizedArea) ||
                    sub.subscriberCode.lowercase().contains(normalizedArea)
                val debtMatch = when (debtFilter) {
                    FieldDebtFilter.ARREARS_ONLY -> sub.currentArrearsAmount > 0.0
                    FieldDebtFilter.NO_ARREARS -> sub.currentArrearsAmount <= 0.0
                    FieldDebtFilter.ALL -> true
                }
                activeMatch && areaMatch && debtMatch
            }
            .sortedWith(compareBy<SubscriberEntity> { it.address.ifEmpty { "zzz" } }.thenBy { it.subscriberCode })
            .map { sub ->
                FieldCollectionSheetItem(
                    subscriberId = sub.id,
                    subscriberCode = sub.subscriberCode,
                    fullName = sub.fullName,
                    address = sub.address.ifBlank { "-" },
                    phoneNumber = sub.phoneNumber.ifBlank { "-" },
                    previousMeterReading = FinancialCalculator.roundCurrency(latestReadingBySub[sub.id] ?: 0.0),
                    currentArrears = FinancialCalculator.roundCurrency(sub.currentArrearsAmount),
                    creditBalance = FinancialCalculator.roundCurrency(sub.currentCreditBalance),
                    customKwhPrice = FinancialCalculator.roundCurrency(sub.customKwhPrice),
                    isActive = sub.isActive
                )
            }
    }

    fun translatePaymentMethod(method: String): String = when (method) {
        "CASH" -> "نقدي"
        "BANK_TRANSFER" -> "تحويل بنكي"
        "MOBILE_WALLET" -> "محفظة إلكترونية"
        "CHECK" -> "شيك"
        else -> method
    }
}
