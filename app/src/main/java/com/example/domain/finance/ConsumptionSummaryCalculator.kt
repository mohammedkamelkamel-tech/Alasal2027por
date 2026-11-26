package com.example.domain.finance

import com.example.data.local.entity.MeterReadingEntity
import com.example.data.local.entity.SubscriberEntity
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class SubscriberHalfConsumptionItem(
    val readingId: Long,
    val subscriberId: Long,
    val subscriberCode: String,
    val subscriberName: String,
    val meterNumber: String,
    val previousReading: Double,
    val currentReading: Double,
    val consumedKwh: Double,
    val readingDate: Long,
    val readingHalf: Int,
    val billingPeriod: String,
    val hasInvoice: Boolean
)

data class HalfCycleSummary(
    val readingHalf: Int,
    val titleAr: String,
    val shortTitleAr: String,
    val totalConsumedKwh: Double,
    val metersReadCount: Int,
    val items: List<SubscriberHalfConsumptionItem>
)

data class MonthlyConsumptionSummary(
    val year: Int,
    val month: Int,
    val periodKey: String,
    val monthTitleAr: String,
    val firstHalf: HalfCycleSummary,
    val secondHalf: HalfCycleSummary,
    val totalMonthlyKwh: Double,
    val firstHalfMetersCount: Int,
    val secondHalfMetersCount: Int,
    val totalReadingsCount: Int
)

/**
 * Pure domain calculator for Monthly and Semi-Monthly (First Half / Second Half)
 * electricity consumption summaries derived directly from Room MeterReadingEntity records.
 * Strictly aggregates net consumption (current - previous) rather than cumulative meter indices.
 */
object ConsumptionSummaryCalculator {

    const val READING_HALF_FIRST = 1
    const val READING_HALF_SECOND = 2

    private val ARABIC_MONTHS = mapOf(
        1 to "يناير",
        2 to "فبراير",
        3 to "مارس",
        4 to "أبريل",
        5 to "مايو",
        6 to "يونيو",
        7 to "يوليو",
        8 to "أغسطس",
        9 to "سبتمبر",
        10 to "أكتوبر",
        11 to "نوفمبر",
        12 to "ديسمبر"
    )

    fun arabicMonthName(month: Int): String = ARABIC_MONTHS[month] ?: "شهر $month"

    fun monthDisplayTitle(month: Int): String = "شهر $month - ${arabicMonthName(month)}"

    fun halfDisplayTitle(readingHalf: Int): String = when (readingHalf) {
        READING_HALF_SECOND -> "النصف الثاني من الشهر (16 - نهاية الشهر)"
        else -> "النصف الأول من الشهر (1 - 15)"
    }

    fun halfShortTitle(readingHalf: Int): String = when (readingHalf) {
        READING_HALF_SECOND -> "النصف الثاني"
        else -> "النصف الأول"
    }

    /**
     * Automatically classifies a reading timestamp into First Half (days 1..15)
     * or Second Half (days 16..end of month).
     */
    fun classifyHalfFromDate(
        readingDateMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = readingDateMillis }
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        return if (dayOfMonth <= 15) READING_HALF_FIRST else READING_HALF_SECOND
    }

    fun normalizeHalf(readingHalf: Int): Int =
        if (readingHalf == READING_HALF_SECOND) READING_HALF_SECOND else READING_HALF_FIRST

    /**
     * Resolves (year, month 1..12) from billingPeriod ("YYYY-MM", "YYYY/M", etc.)
     * with automatic fallback to readingDateMillis when billingPeriod is non-standard.
     */
    fun resolveYearMonth(
        billingPeriod: String,
        readingDateMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Int, Int> {
        val match = Regex("""(\d{4})[-/](\d{1,2})""").find(billingPeriod.trim())
        if (match != null) {
            val y = match.groupValues[1].toIntOrNull()
            val m = match.groupValues[2].toIntOrNull()
            if (y != null && y in 1970..2200 && m != null && m in 1..12) {
                return y to m
            }
        }
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = readingDateMillis }
        return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    fun formatPeriodKey(year: Int, month: Int): String =
        String.format(Locale.US, "%04d-%02d", year, month.coerceIn(1, 12))

    fun extractAvailableYears(
        readings: List<MeterReadingEntity>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<Int> {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = referenceTimeMillis }
        val currentYear = cal.get(Calendar.YEAR)
        val years = mutableSetOf(currentYear, 2027)
        readings.forEach { reading ->
            val (year, _) = resolveYearMonth(reading.billingPeriod, reading.readingDate, timeZone)
            years.add(year)
        }
        return years.sortedDescending()
    }

    fun buildYearMonthlySummaries(
        year: Int,
        readings: List<MeterReadingEntity>,
        subscribers: List<SubscriberEntity>,
        invoicedReadingIds: Set<Long> = emptySet(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<MonthlyConsumptionSummary> {
        val subscriberMap = subscribers.associateBy { it.id }

        val readingsByMonth = mutableMapOf<Int, MutableList<MeterReadingEntity>>()
        readings.forEach { reading ->
            val (rYear, rMonth) = resolveYearMonth(reading.billingPeriod, reading.readingDate, timeZone)
            if (rYear == year) {
                readingsByMonth.getOrPut(rMonth) { mutableListOf() }.add(reading)
            }
        }

        return (1..12).map { month ->
            val monthReadings = readingsByMonth[month].orEmpty()
            buildSingleMonthSummary(
                year = year,
                month = month,
                monthReadings = monthReadings,
                subscriberMap = subscriberMap,
                invoicedReadingIds = invoicedReadingIds
            )
        }
    }

    fun buildMonthSummary(
        year: Int,
        month: Int,
        readings: List<MeterReadingEntity>,
        subscribers: List<SubscriberEntity>,
        invoicedReadingIds: Set<Long> = emptySet(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): MonthlyConsumptionSummary {
        val subscriberMap = subscribers.associateBy { it.id }
        val monthReadings = readings.filter { reading ->
            val (rYear, rMonth) = resolveYearMonth(reading.billingPeriod, reading.readingDate, timeZone)
            rYear == year && rMonth == month
        }
        return buildSingleMonthSummary(
            year = year,
            month = month,
            monthReadings = monthReadings,
            subscriberMap = subscriberMap,
            invoicedReadingIds = invoicedReadingIds
        )
    }

    private fun buildSingleMonthSummary(
        year: Int,
        month: Int,
        monthReadings: List<MeterReadingEntity>,
        subscriberMap: Map<Long, SubscriberEntity>,
        invoicedReadingIds: Set<Long>
    ): MonthlyConsumptionSummary {
        val sortedItems = monthReadings
            .sortedWith(compareBy<MeterReadingEntity> { it.readingDate }.thenBy { it.id })
            .map { reading ->
                val sub = subscriberMap[reading.subscriberId]
                val netConsumed = resolveNetConsumption(reading)
                SubscriberHalfConsumptionItem(
                    readingId = reading.id,
                    subscriberId = reading.subscriberId,
                    subscriberCode = sub?.subscriberCode ?: "#${reading.subscriberId}",
                    subscriberName = sub?.fullName ?: "مشترك #${reading.subscriberId}",
                    meterNumber = sub?.meterNumber ?: "-",
                    previousReading = FinancialCalculator.roundCurrency(reading.previousReading),
                    currentReading = FinancialCalculator.roundCurrency(reading.currentReading),
                    consumedKwh = netConsumed,
                    readingDate = reading.readingDate,
                    readingHalf = normalizeHalf(reading.readingHalf),
                    billingPeriod = reading.billingPeriod,
                    hasInvoice = reading.id in invoicedReadingIds
                )
            }

        val firstHalfItems = sortedItems.filter { it.readingHalf == READING_HALF_FIRST }
        val secondHalfItems = sortedItems.filter { it.readingHalf == READING_HALF_SECOND }

        val firstHalfTotal = FinancialCalculator.roundCurrency(firstHalfItems.sumOf { it.consumedKwh })
        val secondHalfTotal = FinancialCalculator.roundCurrency(secondHalfItems.sumOf { it.consumedKwh })
        val grandTotal = FinancialCalculator.roundCurrency(firstHalfTotal + secondHalfTotal)

        return MonthlyConsumptionSummary(
            year = year,
            month = month,
            periodKey = formatPeriodKey(year, month),
            monthTitleAr = monthDisplayTitle(month),
            firstHalf = HalfCycleSummary(
                readingHalf = READING_HALF_FIRST,
                titleAr = "القراءة الأولى — النصف الأول من الشهر",
                shortTitleAr = "النصف الأول",
                totalConsumedKwh = firstHalfTotal,
                metersReadCount = firstHalfItems.size,
                items = firstHalfItems
            ),
            secondHalf = HalfCycleSummary(
                readingHalf = READING_HALF_SECOND,
                titleAr = "القراءة الثانية — النصف الثاني من الشهر",
                shortTitleAr = "النصف الثاني",
                totalConsumedKwh = secondHalfTotal,
                metersReadCount = secondHalfItems.size,
                items = secondHalfItems
            ),
            totalMonthlyKwh = grandTotal,
            firstHalfMetersCount = firstHalfItems.size,
            secondHalfMetersCount = secondHalfItems.size,
            totalReadingsCount = firstHalfItems.size + secondHalfItems.size
        )
    }

    /**
     * Strictly uses net consumption (currentReading - previousReading) rather than cumulative meter reading.
     * Respects authorized low-reading override where consumedKwh stores consumption since meter reset.
     */
    fun resolveNetConsumption(reading: MeterReadingEntity): Double {
        return if (reading.isLowReadingOverride) {
            FinancialCalculator.roundCurrency(reading.consumedKwh.coerceAtLeast(0.0))
        } else {
            FinancialCalculator.roundCurrency((reading.currentReading - reading.previousReading).coerceAtLeast(0.0))
        }
    }
}
