package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kwh_tariffs",
    indices = [
        Index(value = ["tariff_code"], unique = true),
        Index(value = ["subscription_type"])
    ]
)
data class KwhTariffEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "tariff_code")
    val tariffCode: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "subscription_type")
    val subscriptionType: String,
    @ColumnInfo(name = "price_per_kwh")
    val pricePerKwh: Double,
    @ColumnInfo(name = "fixed_fee")
    val fixedFee: Double = 0.0,
    @ColumnInfo(name = "effective_from")
    val effectiveFrom: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
