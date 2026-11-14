package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_settings",
    indices = [
        Index(value = ["category"])
    ]
)
data class AppSettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "setting_key")
    val settingKey: String,
    @ColumnInfo(name = "setting_value")
    val settingValue: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "description_ar")
    val descriptionAr: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
