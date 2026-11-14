package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MeterReadingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meter_reading_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = KwhTariffEntity::class,
            parentColumns = ["id"],
            childColumns = ["tariff_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["invoice_number"], unique = true),
        Index(value = ["meter_reading_id"], unique = true),
        Index(value = ["subscriber_id"]),
        Index(value = ["tariff_id"]),
        Index(value = ["billing_period"]),
        Index(value = ["status"]),
        Index(value = ["carried_over_to_invoice_id"])
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "invoice_number")
    val invoiceNumber: String,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long,
    @ColumnInfo(name = "meter_reading_id")
    val meterReadingId: Long? = null,
    @ColumnInfo(name = "tariff_id")
    val tariffId: Long,
    @ColumnInfo(name = "billing_period")
    val billingPeriod: String,
    @ColumnInfo(name = "previous_reading")
    val previousReading: Double = 0.0,
    @ColumnInfo(name = "current_reading")
    val currentReading: Double = 0.0,
    @ColumnInfo(name = "consumed_kwh")
    val consumedKwh: Double,
    @ColumnInfo(name = "unit_price_applied")
    val unitPriceApplied: Double,
    @ColumnInfo(name = "fixed_fee_applied")
    val fixedFeeApplied: Double,
    @ColumnInfo(name = "energy_amount")
    val energyAmount: Double,
    @ColumnInfo(name = "previous_arrears_applied")
    val previousArrearsApplied: Double = 0.0,
    @ColumnInfo(name = "credit_deducted")
    val creditDeducted: Double = 0.0,
    @ColumnInfo(name = "net_payable_amount")
    val netPayableAmount: Double,
    @ColumnInfo(name = "paid_amount")
    val paidAmount: Double = 0.0,
    @ColumnInfo(name = "remaining_amount")
    val remainingAmount: Double = 0.0,
    @ColumnInfo(name = "carried_over_to_invoice_id")
    val carriedOverToInvoiceId: Long? = null,
    @ColumnInfo(name = "status")
    val status: String = "UNPAID",
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "price_modified_at")
    val priceModifiedAt: Long? = null,
    @ColumnInfo(name = "price_modified_by")
    val priceModifiedBy: String? = null,
    @ColumnInfo(name = "price_modification_reason")
    val priceModificationReason: String? = null,
    @ColumnInfo(name = "issued_at")
    val issuedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "due_date")
    val dueDate: Long = System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000
)
