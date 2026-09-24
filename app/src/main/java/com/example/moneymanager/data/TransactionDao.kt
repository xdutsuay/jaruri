package com.example.moneymanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dateTimestamp BETWEEN :start AND :end")
    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    /** Dedup helper: count rows whose memo contains the SMS hash tag, e.g. `[sms:abc]`. */
    @Query("SELECT COUNT(*) FROM transactions WHERE memo LIKE '%' || :tag || '%'")
    suspend fun countByMemoTag(tag: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}
