package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "permissions",
    indices = [
        Index(value = ["role_code", "permission_key"], unique = true),
        Index(value = ["role_code"])
    ]
)
data class PermissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "role_code")
    val roleCode: String,
    @ColumnInfo(name = "permission_key")
    val permissionKey: String,
    @ColumnInfo(name = "is_allowed")
    val isAllowed: Boolean,
    @ColumnInfo(name = "description_ar")
    val descriptionAr: String
)
