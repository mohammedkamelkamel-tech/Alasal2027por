package com.example.domain.finance

import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.SubscriberEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolves and formats exact electricity consumption period dates:
 * - Start ("من"): issue date of the subscriber's latest previous invoice; falls back to
 *   subscriber.subscriptionDate when the subscriber has no prior invoices.
 * - End ("إلى"): current invoice issue date or current meter reading date.
 */
object ConsumptionPeriodHelper {

    fun formatDateDayMonthYear(millis: Long): String {
        val formatter = SimpleDateFormat("d/M/yyyy", Locale.US)
        return formatter.format(Date(millis))
    }

    fun formatPeriodRange(fromMillis: Long, toMillis: Long): String {
        val safeFrom = if (fromMillis > 0L) fromMillis else toMillis
        val safeTo = if (toMillis > 0L) toMillis else safeFrom
        return "الفترة: من ${formatDateDayMonthYear(safeFrom)} إلى ${formatDateDayMonthYear(safeTo)}"
    }

    /**
     * Resolves the consumption start date for a subscriber:
     * - When [currentInvoice] is provided: returns the latest invoice issued strictly before
     *   [currentInvoice] (or with same timestamp and smaller id). If none exist, falls back to
     *   [subscriber.subscriptionDate], or [currentInvoice.issuedAt].
     * - When [currentInvoice] is null (new meter reading entry): returns the latest existing
     *   invoice's [InvoiceEntity.issuedAt] for [subscriber]. If none exist, falls back to
     *   [subscriber.subscriptionDate], or [fallbackEndMillis].
     */
    fun resolvePreviousInvoiceDate(
        subscriber: SubscriberEntity?,
        allInvoices: List<InvoiceEntity>,
        currentInvoice: InvoiceEntity? = null,
        fallbackEndMillis: Long = System.currentTimeMillis()
    ): Long {
        val subscriberId = currentInvoice?.subscriberId ?: subscriber?.id
        if (subscriberId != null) {
            val previousInvoice = if (currentInvoice != null) {
                allInvoices
                    .asSequence()
                    .filter { it.subscriberId == subscriberId && it.id != currentInvoice.id }
                    .filter {
                        it.issuedAt < currentInvoice.issuedAt ||
                            (it.issuedAt == currentInvoice.issuedAt && it.id < currentInvoice.id)
                    }
                    .maxWithOrNull(compareBy<InvoiceEntity> { it.issuedAt }.thenBy { it.id })
            } else {
                allInvoices
                    .asSequence()
                    .filter { it.subscriberId == subscriberId }
                    .maxWithOrNull(compareBy<InvoiceEntity> { it.issuedAt }.thenBy { it.id })
            }
            if (previousInvoice != null) {
                return previousInvoice.issuedAt
            }
        }
        val subDate = subscriber?.subscriptionDate
        if (subDate != null && subDate > 0L) {
            return subDate
        }
        return currentInvoice?.issuedAt ?: fallbackEndMillis
    }

    fun formatInvoicePeriodRange(
        invoice: InvoiceEntity,
        subscriber: SubscriberEntity?,
        allInvoices: List<InvoiceEntity>
    ): String {
        val fromMillis = resolvePreviousInvoiceDate(
            subscriber = subscriber,
            allInvoices = allInvoices,
            currentInvoice = invoice,
            fallbackEndMillis = invoice.issuedAt
        )
        return formatPeriodRange(fromMillis = fromMillis, toMillis = invoice.issuedAt)
    }

    fun formatReadingPeriodRange(
        subscriber: SubscriberEntity?,
        allInvoices: List<InvoiceEntity>,
        readingDateMillis: Long
    ): String {
        val fromMillis = resolvePreviousInvoiceDate(
            subscriber = subscriber,
            allInvoices = allInvoices,
            currentInvoice = null,
            fallbackEndMillis = readingDateMillis
        )
        return formatPeriodRange(fromMillis = fromMillis, toMillis = readingDateMillis)
    }
}
