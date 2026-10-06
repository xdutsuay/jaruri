package com.example.moneymanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PayeeDao {
    @Query("SELECT * FROM payees ORDER BY displayName COLLATE NOCASE")
    fun getAll(): Flow<List<PayeeEntity>>

    @Query("SELECT * FROM payees")
    suspend fun getAllList(): List<PayeeEntity>

    @Query("SELECT * FROM payees WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PayeeEntity?

    @Query("SELECT * FROM payees WHERE normalizedKey = :key LIMIT 1")
    suspend fun getByNormalizedKey(key: String): PayeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payee: PayeeEntity): Long

    @Update
    suspend fun update(payee: PayeeEntity)

    @Query("SELECT * FROM payee_aliases WHERE aliasKey = :aliasKey LIMIT 1")
    suspend fun getAlias(aliasKey: String): PayeeAliasEntity?

    @Query("SELECT * FROM payee_aliases WHERE payeeId = :payeeId")
    suspend fun getAliasesForPayee(payeeId: Long): List<PayeeAliasEntity>

    @Query("SELECT * FROM payee_aliases")
    suspend fun getAllAliases(): List<PayeeAliasEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlias(alias: PayeeAliasEntity): Long

    @Query(
        """
        UPDATE transactions SET memo = REPLACE(memo, :oldPrefix, :newPrefix)
        WHERE payeeId = :payeeId AND deletedAt IS NULL AND memo LIKE :oldPrefix || '%'
        """
    )
    suspend fun rewriteMemosForPayee(payeeId: Long, oldPrefix: String, newPrefix: String)

    @Query(
        """
        UPDATE transactions SET memo = :newMemo
        WHERE id = :txId
        """
    )
    suspend fun updateMemo(txId: Long, newMemo: String)
}
