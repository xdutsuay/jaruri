package com.example.moneymanager.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Snapshot of SMS-reported balance vs ledger balance for an account/instrument.
 * Enables a running difference trail without hardcoding bank formats.
 */
@Entity(tableName = "balance_observations")
data class BalanceObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Balance figure from the SMS. */
    val reportedBalance: Double,
    /** Kind string: AVAILABLE, OUTSTANDING, AVAILABLE_LIMIT */
    val balanceKind: String,
    /** App ledger balance at observation time. */
    val ledgerBalance: Double,
    /** reported - ledger (or kind-specific comparable). */
    val difference: Double,
    val smsHash: String = "",
    val transactionId: Long? = null,
    val observedAt: Long = System.currentTimeMillis()
)

@Dao
interface BalanceObservationDao {
    @Query(
        "SELECT * FROM balance_observations WHERE accountId = :accountId ORDER BY observedAt DESC LIMIT :limit"
    )
    fun recentForAccount(accountId: Long, limit: Int = 50): Flow<List<BalanceObservationEntity>>

    @Query(
        "SELECT * FROM balance_observations ORDER BY observedAt DESC LIMIT :limit"
    )
    fun recent(limit: Int = 100): Flow<List<BalanceObservationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: BalanceObservationEntity): Long

    @Query("SELECT * FROM balance_observations WHERE accountId = :accountId ORDER BY observedAt DESC LIMIT 1")
    suspend fun latest(accountId: Long): BalanceObservationEntity?
}
