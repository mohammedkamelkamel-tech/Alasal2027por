package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ArrearsEntity
import com.example.data.local.entity.CreditBalanceEntity
import com.example.data.local.entity.InvoiceEntity
import com.example.data.local.entity.MeterReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillingDao {
    @Query("SELECT * FROM meter_readings ORDER BY reading_date DESC, id DESC")
    fun observeAllMeterReadings(): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE subscriber_id = :subscriberId ORDER BY reading_date DESC, id DESC")
    fun observeReadingsForSubscriber(subscriberId: Long): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE subscriber_id = :subscriberId ORDER BY reading_date DESC, id DESC LIMIT 1")
    suspend fun getLatestReadingForSubscriber(subscriberId: Long): MeterReadingEntity?

    @Query("SELECT * FROM meter_readings WHERE id = :readingId")
    suspend fun getReadingById(readingId: Long): MeterReadingEntity?

    @Query("SELECT * FROM invoices ORDER BY issued_at DESC, id DESC")
    fun observeAllInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT COUNT(*) FROM invoices")
    fun observeInvoiceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun countInvoices(): Int

    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    suspend fun getInvoiceById(invoiceId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE meter_reading_id = :readingId LIMIT 1")
    suspend fun getInvoiceByReadingId(readingId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE subscriber_id = :subscriberId ORDER BY issued_at DESC, id DESC")
    fun observeInvoicesForSubscriber(subscriberId: Long): Flow<List<InvoiceEntity>>

    @Query(
        """
        SELECT * FROM invoices
        WHERE subscriber_id = :subscriberId
          AND carried_over_to_invoice_id IS NULL
          AND remaining_amount > 0.0
          AND status IN ('UNPAID', 'PARTIAL')
        ORDER BY issued_at ASC, id ASC
        """
    )
    suspend fun getActiveUnpaidInvoicesForCarryOver(subscriberId: Long): List<InvoiceEntity>

    @Query(
        """
        SELECT COALESCE(SUM(remaining_amount), 0.0) FROM invoices
        WHERE subscriber_id = :subscriberId
          AND carried_over_to_invoice_id IS NULL
          AND remaining_amount > 0.0
          AND status IN ('UNPAID', 'PARTIAL')
        """
    )
    suspend fun sumActiveUnpaidInvoiceBalances(subscriberId: Long): Double

    @Query("SELECT * FROM invoices WHERE carried_over_to_invoice_id = :targetInvoiceId ORDER BY issued_at ASC")
    suspend fun getInvoicesCarriedOverInto(targetInvoiceId: Long): List<InvoiceEntity>

    @Query(
        "UPDATE invoices SET carried_over_to_invoice_id = :targetInvoiceId, status = 'CARRIED_OVER' WHERE id = :sourceInvoiceId"
    )
    suspend fun markInvoiceCarriedOver(sourceInvoiceId: Long, targetInvoiceId: Long)

    @Query("SELECT COALESCE(SUM(remaining_amount), 0.0) FROM invoices WHERE carried_over_to_invoice_id IS NULL AND status IN ('UNPAID', 'PARTIAL')")
    fun observeTotalReceivables(): Flow<Double>

    @Query("SELECT COUNT(*) FROM invoices WHERE status = 'PAID'")
    fun observePaidInvoicesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM invoices WHERE status = 'UNPAID' AND carried_over_to_invoice_id IS NULL")
    fun observeUnpaidInvoicesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM invoices WHERE status = 'PARTIAL' AND carried_over_to_invoice_id IS NULL")
    fun observePartialInvoicesCount(): Flow<Int>

    @Query("SELECT * FROM arrears WHERE subscriber_id = :subscriberId AND status != 'SETTLED' AND carried_over_to_invoice_id IS NULL ORDER BY recorded_at ASC")
    suspend fun getOpenArrearsForSubscriber(subscriberId: Long): List<ArrearsEntity>

    @Query("SELECT * FROM arrears ORDER BY recorded_at DESC, id DESC")
    fun observeAllArrears(): Flow<List<ArrearsEntity>>

    @Query("SELECT * FROM arrears WHERE subscriber_id = :subscriberId ORDER BY recorded_at DESC, id DESC")
    fun observeArrearsForSubscriber(subscriberId: Long): Flow<List<ArrearsEntity>>

    @Query("SELECT * FROM credit_balances WHERE subscriber_id = :subscriberId ORDER BY recorded_at DESC, id DESC")
    fun observeCreditLedgerForSubscriber(subscriberId: Long): Flow<List<CreditBalanceEntity>>

    @Query("SELECT * FROM credit_balances ORDER BY recorded_at DESC, id DESC")
    fun observeAllCreditTransactions(): Flow<List<CreditBalanceEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeterReading(reading: MeterReadingEntity): Long

    @Update
    suspend fun updateMeterReading(reading: MeterReadingEntity)

    @Query("SELECT * FROM meter_readings ORDER BY reading_date ASC, id ASC")
    suspend fun getAllMeterReadingsOnce(): List<MeterReadingEntity>

    @Query("SELECT * FROM meter_readings WHERE subscriber_id = :subscriberId AND billing_period = :billingPeriod LIMIT 1")
    suspend fun getReadingBySubscriberAndPeriod(subscriberId: Long, billingPeriod: String): MeterReadingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Query("SELECT * FROM invoices ORDER BY issued_at ASC, id ASC")
    suspend fun getAllInvoicesOnce(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE invoice_number = :invoiceNumber LIMIT 1")
    suspend fun getInvoiceByNumber(invoiceNumber: String): InvoiceEntity?

    @Query("DELETE FROM invoices WHERE id = :invoiceId")
    suspend fun deleteInvoiceById(invoiceId: Long)

    @Query("UPDATE invoices SET carried_over_to_invoice_id = NULL, status = CASE WHEN paid_amount > 0 THEN 'PARTIAL' ELSE 'UNPAID' END WHERE carried_over_to_invoice_id = :deletedInvoiceId")
    suspend fun restoreCarriedOverInvoicesForDeleted(deletedInvoiceId: Long)

    @Query("DELETE FROM arrears WHERE carried_over_to_invoice_id = :deletedInvoiceId")
    suspend fun deleteCarryOverArrearsForDeletedInvoice(deletedInvoiceId: Long)

    @Query("SELECT * FROM arrears ORDER BY recorded_at ASC, id ASC")
    suspend fun getAllArrearsOnce(): List<ArrearsEntity>

    @Query("SELECT * FROM credit_balances ORDER BY recorded_at ASC, id ASC")
    suspend fun getAllCreditTransactionsOnce(): List<CreditBalanceEntity>

    @Update
    suspend fun updateInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArrears(arrears: ArrearsEntity): Long

    @Update
    suspend fun updateArrears(arrears: ArrearsEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCreditTransaction(credit: CreditBalanceEntity): Long
}
