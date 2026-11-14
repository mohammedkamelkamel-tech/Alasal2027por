package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "arrears",
    foreignKeys = [
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_invoice_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["subscriber_id"]),
        Index(value = ["source_invoice_id"]),
        Index(value = ["status"]),
        Index(value = ["carried_over_to_invoice_id"])
    ]
)
data class ArrearsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long,
    @ColumnInfo(name = "source_invoice_id")
    val sourceInvoiceId: Long? = null,
    @ColumnInfo(name = "carried_over_to_invoice_id")
    val carriedOverToInvoiceId: Long? = null,
    @ColumnInfo(name = "original_amount")
    val originalAmount: Double,
    @ColumnInfo(name = "remaining_amount")
    val remainingAmount: Double,
    @ColumnInfo(name = "status")
    val status: String = "OPEN",
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "settled_at")
    val settledAt: Long? = null,
    @ColumnInfo(name = "notes")
    val notes: String = ""
)
