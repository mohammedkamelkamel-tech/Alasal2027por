package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "financial_audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["actor_user_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["subscriber_id"]),
        Index(value = ["actor_user_id"]),
        Index(value = ["operation_type"]),
        Index(value = ["created_at"])
    ]
)
data class FinancialAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long? = null,
    @ColumnInfo(name = "actor_user_id")
    val actorUserId: Long? = null,
    @ColumnInfo(name = "actor_name_snapshot")
    val actorNameSnapshot: String = "النظام",
    @ColumnInfo(name = "operation_type")
    val operationType: String,
    @ColumnInfo(name = "entity_name")
    val entityName: String,
    @ColumnInfo(name = "entity_id")
    val entityId: Long? = null,
    @ColumnInfo(name = "amount_before")
    val amountBefore: Double? = null,
    @ColumnInfo(name = "amount_after")
    val amountAfter: Double? = null,
    @ColumnInfo(name = "details_ar")
    val detailsAr: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
