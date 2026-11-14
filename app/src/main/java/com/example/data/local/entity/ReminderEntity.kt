package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoice_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["subscriber_id"]),
        Index(value = ["invoice_id"]),
        Index(value = ["scheduled_at"]),
        Index(value = ["status"])
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long? = null,
    @ColumnInfo(name = "invoice_id")
    val invoiceId: Long? = null,
    @ColumnInfo(name = "reminder_type")
    val reminderType: String = "METER_READING",
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "message")
    val message: String,
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: Long,
    @ColumnInfo(name = "status")
    val status: String = "PENDING",
    @ColumnInfo(name = "channel")
    val channel: String = "LOCAL_NOTIFICATION",
    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int = 0,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
