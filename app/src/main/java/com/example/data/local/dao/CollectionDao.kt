package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionWithReceipt
import com.example.data.local.entity.ReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY collected_at DESC, id DESC")
    fun observeAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT COUNT(*) FROM collections")
    fun observeCollectionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun countCollections(): Int

    @Query("SELECT COUNT(*) FROM receipts")
    suspend fun countReceipts(): Int

    @Query("SELECT COALESCE(SUM(amount_paid), 0.0) FROM collections")
    fun observeTotalCollectedAmount(): Flow<Double>

    @Query("SELECT * FROM collections WHERE invoice_id = :invoiceId ORDER BY collected_at DESC")
    suspend fun getCollectionsForInvoice(invoiceId: Long): List<CollectionEntity>

    @Transaction
    @Query("SELECT * FROM collections ORDER BY collected_at DESC, id DESC")
    fun observeAllCollectionsWithReceipts(): Flow<List<CollectionWithReceipt>>

    @Transaction
    @Query("SELECT * FROM collections WHERE subscriber_id = :subscriberId ORDER BY collected_at DESC, id DESC")
    fun observeCollectionsWithReceipts(subscriberId: Long): Flow<List<CollectionWithReceipt>>

    @Query("SELECT * FROM receipts ORDER BY issued_at DESC, id DESC")
    fun observeAllReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE collection_id = :collectionId LIMIT 1")
    suspend fun getReceiptByCollectionId(collectionId: Long): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptById(receiptId: Long): ReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE receipt_number = :receiptNumber")
    suspend fun getReceiptByNumber(receiptNumber: String): ReceiptEntity?

    @Query("SELECT * FROM collections WHERE collection_reference = :reference LIMIT 1")
    suspend fun getCollectionByReference(reference: String): CollectionEntity?

    @Query("SELECT * FROM collections ORDER BY collected_at ASC, id ASC")
    suspend fun getAllCollectionsOnce(): List<CollectionEntity>

    @Query("SELECT * FROM receipts ORDER BY issued_at ASC, id ASC")
    suspend fun getAllReceiptsOnce(): List<ReceiptEntity>
}
