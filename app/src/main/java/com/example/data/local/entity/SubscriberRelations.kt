package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Full relational financial history projection for a subscriber.
 * Preserves complete auditability across readings, invoices, collections, receipts, arrears, credits, tariff logs, and audit logs.
 */
data class SubscriberFullHistory(
    @Embedded val subscriber: SubscriberEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val meterReadings: List<MeterReadingEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val invoices: List<InvoiceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val collections: List<CollectionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val receipts: List<ReceiptEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val arrears: List<ArrearsEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val creditBalances: List<CreditBalanceEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val tariffChangeLogs: List<TariffChangeLogEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val reminders: List<ReminderEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "subscriber_id"
    )
    val auditLogs: List<FinancialAuditLogEntity>
)

data class TariffWithHistory(
    @Embedded val tariff: KwhTariffEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "tariff_id"
    )
    val changeLogs: List<TariffChangeLogEntity>
)

data class CollectionWithReceipt(
    @Embedded val collection: CollectionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "collection_id"
    )
    val receipt: ReceiptEntity?
)
