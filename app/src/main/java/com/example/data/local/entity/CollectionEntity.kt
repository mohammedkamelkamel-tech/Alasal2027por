package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collections",
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
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["collected_by_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["collection_reference"], unique = true),
        Index(value = ["subscriber_id"]),
        Index(value = ["invoice_id"]),
        Index(value = ["collected_by_user_id"]),
        Index(value = ["collected_at"])
    ]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "collection_reference")
    val collectionReference: String,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long,
    @ColumnInfo(name = "invoice_id")
    val invoiceId: Long? = null,
    @ColumnInfo(name = "collected_by_user_id")
    val collectedByUserId: Long? = null,
    @ColumnInfo(name = "collector_name_snapshot")
    val collectorNameSnapshot: String = "مدير محطة العسل",
    @ColumnInfo(name = "amount_paid")
    val amountPaid: Double,
    @ColumnInfo(name = "previous_balance_snapshot")
    val previousBalanceSnapshot: Double = 0.0,
    @ColumnInfo(name = "remaining_after_snapshot")
    val remainingAfterSnapshot: Double = 0.0,
    @ColumnInfo(name = "applied_to_invoice")
    val appliedToInvoice: Double = 0.0,
    @ColumnInfo(name = "applied_to_arrears")
    val appliedToArrears: Double = 0.0,
    @ColumnInfo(name = "added_to_credit")
    val addedToCredit: Double = 0.0,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String = "CASH",
    @ColumnInfo(name = "collected_at")
    val collectedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes")
    val notes: String = ""
)
