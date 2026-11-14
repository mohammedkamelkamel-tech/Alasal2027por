package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["role_code"])
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    @ColumnInfo(name = "role_code")
    val roleCode: String,
    @ColumnInfo(name = "pin_hash")
    val pinHash: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "custom_permissions_json")
    val customPermissionsJson: String = "",
    @ColumnInfo(name = "biometric_enabled")
    val biometricEnabled: Boolean = false,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_id")
    val syncId: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
