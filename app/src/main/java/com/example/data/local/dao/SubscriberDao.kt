package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.SubscriberEntity
import com.example.data.local.entity.SubscriberFullHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriberDao {
    @Query("SELECT * FROM subscribers ORDER BY full_name ASC")
    fun observeAllSubscribers(): Flow<List<SubscriberEntity>>

    @Query("SELECT * FROM subscribers ORDER BY full_name ASC")
    suspend fun getAllSubscribersOnce(): List<SubscriberEntity>

    @Query("SELECT COUNT(*) FROM subscribers")
    fun observeSubscriberCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(current_arrears_amount), 0.0) FROM subscribers")
    fun observeTotalArrears(): Flow<Double>

    @Query("SELECT COALESCE(SUM(current_credit_balance), 0.0) FROM subscribers")
    fun observeTotalCreditBalances(): Flow<Double>

    @Query("SELECT * FROM subscribers WHERE id = :subscriberId")
    suspend fun getById(subscriberId: Long): SubscriberEntity?

    @Query("SELECT * FROM subscribers WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SubscriberEntity>

    @Query("SELECT * FROM subscribers WHERE subscriber_code = :code")
    suspend fun getByCode(code: String): SubscriberEntity?

    @Query("SELECT COUNT(*) FROM subscribers")
    suspend fun countSubscribers(): Int

    @Query(
        """
        SELECT (
            (SELECT COUNT(*) FROM invoices WHERE subscriber_id = :subscriberId) +
            (SELECT COUNT(*) FROM collections WHERE subscriber_id = :subscriberId) +
            (SELECT COUNT(*) FROM meter_readings WHERE subscriber_id = :subscriberId) +
            (SELECT COUNT(*) FROM arrears WHERE subscriber_id = :subscriberId) +
            (SELECT COUNT(*) FROM credit_balances WHERE subscriber_id = :subscriberId)
        )
        """
    )
    suspend fun countFinancialHistoryRecords(subscriberId: Long): Int

    @Transaction
    @Query("SELECT * FROM subscribers WHERE id = :subscriberId")
    suspend fun getSubscriberFullHistory(subscriberId: Long): SubscriberFullHistory?

    @Transaction
    @Query("SELECT * FROM subscribers WHERE id = :subscriberId")
    fun observeSubscriberFullHistory(subscriberId: Long): Flow<SubscriberFullHistory?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subscriber: SubscriberEntity): Long

    @Update
    suspend fun update(subscriber: SubscriberEntity)

    @Query("DELETE FROM subscribers WHERE id = :subscriberId")
    suspend fun deleteById(subscriberId: Long)

    @Query(
        "UPDATE subscribers SET current_arrears_amount = :arrearsAmount, current_credit_balance = :creditBalance, updated_at = :updatedAt WHERE id = :subscriberId"
    )
    suspend fun updateFinancialBalances(
        subscriberId: Long,
        arrearsAmount: Double,
        creditBalance: Double,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE subscribers SET previous_kwh_price = custom_kwh_price, custom_kwh_price = :newPrice, updated_at = :updatedAt WHERE id = :subscriberId"
    )
    suspend fun updateKwhPrice(
        subscriberId: Long,
        newPrice: Double,
        updatedAt: Long = System.currentTimeMillis()
    )
}
