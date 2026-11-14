package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meter_readings",
    foreignKeys = [
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["recorded_by_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["subscriber_id", "billing_period"]),
        Index(value = ["recorded_by_user_id"]),
        Index(value = ["reading_date"])
    ]
)
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long,
    @ColumnInfo(name = "billing_period")
    val billingPeriod: String,
    @ColumnInfo(name = "previous_reading")
    val previousReading: Double,
    @ColumnInfo(name = "current_reading")
    val currentReading: Double,
    @ColumnInfo(name = "consumed_kwh")
    val consumedKwh: Double,
    @ColumnInfo(name = "recorded_by_user_id")
    val recordedByUserId: Long? = null,
    @ColumnInfo(name = "reading_date")
    val readingDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,
    @ColumnInfo(name = "is_low_reading_override")
    val isLowReadingOverride: Boolean = false,
    @ColumnInfo(name = "override_reason")
    val overrideReason: String = "",
    @ColumnInfo(name = "notes")
    val notes: String = ""
)
