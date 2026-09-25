package com.example.moneymanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY dateTimestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL")
    suspend fun getAllActiveList(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun getCount(): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND dateTimestamp BETWEEN :start AND :end"
    )
    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    /** Dedup helper: count rows whose memo contains the SMS hash tag, e.g. `[sms:abc]`. */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND memo LIKE '%' || :tag || '%'"
    )
    suspend fun countByMemoTag(tag: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun hardDeleteById(id: Long)

    @Query("DELETE FROM transactions WHERE deletedAt IS NOT NULL")
    suspend fun emptyRecycleBin()

    @Query("DELETE FROM transactions WHERE deletedAt IS NULL AND memo LIKE '%[sms:%'")
    suspend fun deleteActiveSmsImports()

    @Query("DELETE FROM transactions WHERE deletedAt IS NULL")
    suspend fun deleteAllActive()

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}
