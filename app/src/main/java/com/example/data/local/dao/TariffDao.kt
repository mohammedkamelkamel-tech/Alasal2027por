package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.KwhTariffEntity
import com.example.data.local.entity.TariffChangeLogEntity
import com.example.data.local.entity.TariffWithHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface TariffDao {
    @Query("SELECT * FROM kwh_tariffs ORDER BY is_active DESC, effective_from DESC")
    fun observeAllTariffs(): Flow<List<KwhTariffEntity>>

    @Query("SELECT * FROM kwh_tariffs WHERE is_active = 1 ORDER BY effective_from DESC LIMIT 1")
    fun observePrimaryActiveTariff(): Flow<KwhTariffEntity?>

    @Query("SELECT * FROM kwh_tariffs WHERE is_active = 1 ORDER BY effective_from DESC LIMIT 1")
    suspend fun getPrimaryActiveTariffOnce(): KwhTariffEntity?

    @Query("SELECT * FROM tariff_change_logs ORDER BY changed_at DESC, id DESC")
    fun observeAllTariffChangeLogs(): Flow<List<TariffChangeLogEntity>>

    @Query("SELECT * FROM tariff_change_logs WHERE subscriber_id = :subscriberId ORDER BY changed_at DESC, id DESC")
    fun observeTariffLogsForSubscriber(subscriberId: Long): Flow<List<TariffChangeLogEntity>>

    @Transaction
    @Query("SELECT * FROM kwh_tariffs ORDER BY effective_from DESC")
    fun observeTariffsWithHistory(): Flow<List<TariffWithHistory>>

    @Query("SELECT * FROM kwh_tariffs WHERE id = :tariffId")
    suspend fun getTariffById(tariffId: Long): KwhTariffEntity?

    @Query("SELECT * FROM kwh_tariffs WHERE tariff_code = :code")
    suspend fun getTariffByCode(code: String): KwhTariffEntity?

    @Query("SELECT COUNT(*) FROM kwh_tariffs")
    suspend fun countTariffs(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTariff(tariff: KwhTariffEntity): Long

    @Update
    suspend fun updateTariff(tariff: KwhTariffEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTariffChangeLog(log: TariffChangeLogEntity): Long

    @Query("SELECT * FROM kwh_tariffs ORDER BY id ASC")
    suspend fun getAllTariffsOnce(): List<KwhTariffEntity>

    @Query("SELECT * FROM tariff_change_logs ORDER BY changed_at ASC, id ASC")
    suspend fun getAllTariffChangeLogsOnce(): List<TariffChangeLogEntity>
}
