package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["issued_by_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["receipt_number"], unique = true),
        Index(value = ["collection_id"], unique = true),
        Index(value = ["subscriber_id"]),
        Index(value = ["issued_by_user_id"])
    ]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "receipt_number")
    val receiptNumber: String,
    @ColumnInfo(name = "collection_id")
    val collectionId: Long,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long,
    @ColumnInfo(name = "issued_by_user_id")
    val issuedByUserId: Long? = null,
    @ColumnInfo(name = "collector_name_snapshot")
    val collectorNameSnapshot: String = "مدير محطة العسل",
    @ColumnInfo(name = "subscriber_name_snapshot")
    val subscriberNameSnapshot: String = "",
    @ColumnInfo(name = "subscriber_code_snapshot")
    val subscriberCodeSnapshot: String = "",
    @ColumnInfo(name = "invoice_number_snapshot")
    val invoiceNumberSnapshot: String = "",
    @ColumnInfo(name = "payment_method")
    val paymentMethod: String = "CASH",
    @ColumnInfo(name = "previous_balance")
    val previousBalance: Double = 0.0,
    @ColumnInfo(name = "amount")
    val amount: Double,
    @ColumnInfo(name = "balance_after_receipt")
    val balanceAfterReceipt: Double,
    @ColumnInfo(name = "credit_after_receipt")
    val creditAfterReceipt: Double,
    @ColumnInfo(name = "station_name_snapshot")
    val stationNameSnapshot: String = "محطة العسل",
    @ColumnInfo(name = "station_phone_snapshot")
    val stationPhoneSnapshot: String = "",
    @ColumnInfo(name = "issued_at")
    val issuedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_voided")
    val isVoided: Boolean = false
)
