package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tariff_change_logs",
    foreignKeys = [
        ForeignKey(
            entity = KwhTariffEntity::class,
            parentColumns = ["id"],
            childColumns = ["tariff_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["changed_by_user_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SubscriberEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriber_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["tariff_id"]),
        Index(value = ["changed_by_user_id"]),
        Index(value = ["subscriber_id"]),
        Index(value = ["changed_at"])
    ]
)
data class TariffChangeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "tariff_id")
    val tariffId: Long,
    @ColumnInfo(name = "subscriber_id")
    val subscriberId: Long? = null,
    @ColumnInfo(name = "subscriber_name_snapshot")
    val subscriberNameSnapshot: String? = null,
    @ColumnInfo(name = "old_price_per_kwh")
    val oldPricePerKwh: Double,
    @ColumnInfo(name = "new_price_per_kwh")
    val newPricePerKwh: Double,
    @ColumnInfo(name = "old_fixed_fee")
    val oldFixedFee: Double,
    @ColumnInfo(name = "new_fixed_fee")
    val newFixedFee: Double,
    @ColumnInfo(name = "effective_from")
    val effectiveFrom: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "changed_by_user_id")
    val changedByUserId: Long? = null,
    @ColumnInfo(name = "actor_name_snapshot")
    val actorNameSnapshot: String = "مدير محطة العسل",
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "changed_at")
    val changedAt: Long = System.currentTimeMillis()
)
