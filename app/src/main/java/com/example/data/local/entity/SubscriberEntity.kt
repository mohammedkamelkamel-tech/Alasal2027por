package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscribers",
    indices = [
        Index(value = ["subscriber_code"], unique = true),
        Index(value = ["meter_number"], unique = true),
        Index(value = ["full_name"]),
        Index(value = ["phone_number"])
    ]
)
data class SubscriberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "subscriber_code")
    val subscriberCode: String,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "meter_number")
    val meterNumber: String,
    @ColumnInfo(name = "meter_seal_number")
    val meterSealNumber: String = "",
    @ColumnInfo(name = "address")
    val address: String,
    @ColumnInfo(name = "subscription_type")
    val subscriptionType: String = "منزلي",
    @ColumnInfo(name = "custom_kwh_price")
    val customKwhPrice: Double = 0.45,
    @ColumnInfo(name = "previous_kwh_price")
    val previousKwhPrice: Double? = null,
    @ColumnInfo(name = "subscription_date")
    val subscriptionDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "current_arrears_amount")
    val currentArrearsAmount: Double = 0.0,
    @ColumnInfo(name = "current_credit_balance")
    val currentCreditBalance: Double = 0.0,
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
