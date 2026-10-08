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

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL AND needsCategoryReview = 1"
    )
    fun countNeedsCategoryReview(): Flow<Int>

    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND needsCategoryReview = 1 ORDER BY dateTimestamp DESC"
    )
    fun getNeedsCategoryReview(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET accountId = :toId WHERE accountId = :fromId")
    suspend fun reassignAccount(fromId: Long, toId: Long)

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

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL
          AND dateTimestamp BETWEEN :since AND :until
        ORDER BY dateTimestamp DESC
        """
    )
    suspend fun getRecentForRefundMatch(since: Long, until: Long): List<TransactionEntity>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deletedAt IS NULL AND type = 'EXPENSE'
        ORDER BY dateTimestamp DESC LIMIT 1
        """
    )
    suspend fun getLastExpense(): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND payeeId = :payeeId"
    )
    suspend fun getByPayeeId(payeeId: Long): List<TransactionEntity>

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}
