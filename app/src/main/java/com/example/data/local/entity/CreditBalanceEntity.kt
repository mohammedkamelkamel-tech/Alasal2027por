package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_balances",
    foreignKeys = [
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_collection_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_invoice_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["subscriber_id"]),
        Index(value = ["source_collection_id"]),
        Index(value = ["source_invoice_id"]),
        Index(value = ["recorded_at"])
    ]
)
data class CreditBalanceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long,
    @ColumnInfo(name = "source_collection_id")
    val sourceCollectionId: Long? = null,
    @ColumnInfo(name = "source_invoice_id")
    val sourceInvoiceId: Long? = null,
    @ColumnInfo(name = "transaction_type")
    val transactionType: String,
    @ColumnInfo(name = "amount_delta")
    val amountDelta: Double,
    @ColumnInfo(name = "balance_after")
    val balanceAfter: Double,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes")
    val notes: String = ""
)
